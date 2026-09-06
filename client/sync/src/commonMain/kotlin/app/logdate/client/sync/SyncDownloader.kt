package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableDraftRepository
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.SyncedDraft
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableTextBlock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

/**
 * The concrete per-[EntityType] download side of sync: journals and notes fit
 * [SyncDownloadEngine]'s pluggable-[ConflictResolver] shape and are thin strategy definitions;
 * drafts (a simpler newer-wins heuristic) and associations (additions/deletions, not
 * changes/deletions) don't, and have their own hand-rolled paginate-apply loops instead.
 *
 * @param mapCloudApiError,mapException Reuse [DefaultSyncManager]'s own error mapping (including
 *   its `lastErrorFlow` side effect) instead of duplicating it here, so a download failure is
 *   reported identically to an upload failure.
 */
internal class SyncDownloader(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
    private val cloudJournalDataSource: CloudJournalDataSource,
    private val cloudContentDataSource: CloudContentDataSource,
    private val cloudDraftDataSource: CloudDraftDataSource,
    private val cloudAssociationDataSource: CloudAssociationDataSource,
    private val journalConflictResolver: ConflictResolver<Journal>,
    private val noteConflictResolver: ConflictResolver<JournalNote>,
    private val syncMetadataService: SyncMetadataService,
    private val transactionManager: SyncTransactionManager,
    private val mediaSyncRefStore: MediaSyncRefStore,
    private val downloadEngine: SyncDownloadEngine,
    private val mediaTransfer: SyncMediaTransfer,
    private val tokenRefresher: SyncTokenRefresher,
    private val mapCloudApiError: (CloudApiException) -> SyncResult,
    private val mapException: (Exception, String) -> SyncResult,
) {
    suspend fun downloadJournals(
        accessToken: String,
        since: Instant,
    ): SyncResult {
        val syncableRepository = journalRepository as? SyncableJournalRepository
        return downloadEngine.download(
            strategy =
                DownloadStrategy(
                    entityType = EntityType.JOURNAL,
                    logLabel = "journal",
                    fetchChanges = { token, cursor ->
                        tokenRefresher
                            .withFreshToken(
                                { t -> cloudJournalDataSource.getJournalChanges(t, cursor, SYNC_PAGE_SIZE) },
                                "getJournalChanges",
                            ).map { ChangesPage(it.changes, it.deletions, it.lastSyncTimestamp, it.hasMore) }
                    },
                    localItems = { journalRepository.allJournalsObserved.first().associateBy { it.id } },
                    idOf = { it.id },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = journalConflictResolver,
                    applyCreate = { journal ->
                        if (syncableRepository != null) syncableRepository.createFromSync(journal) else journalRepository.create(journal)
                    },
                    applyReplace = { _, replacement ->
                        if (syncableRepository != null) {
                            syncableRepository.updateFromSync(replacement)
                        } else {
                            journalRepository.update(replacement)
                        }
                    },
                    applyDelete = { id ->
                        if (syncableRepository != null) syncableRepository.deleteFromSync(id) else journalRepository.delete(id)
                    },
                ),
            accessToken = accessToken,
            since = since,
        )
    }

    suspend fun downloadContent(
        accessToken: String,
        since: Instant,
    ): SyncResult {
        val syncableRepository = journalNotesRepository as? SyncableJournalNotesRepository
        return downloadEngine.download(
            strategy =
                DownloadStrategy(
                    entityType = EntityType.NOTE,
                    logLabel = "note",
                    fetchChanges = { token, cursor ->
                        tokenRefresher
                            .withFreshToken(
                                { t -> cloudContentDataSource.getContentChanges(t, cursor, SYNC_PAGE_SIZE) },
                                "getContentChanges",
                            ).map { ChangesPage(it.changes, it.deletions, it.lastSyncTimestamp, it.hasMore) }
                    },
                    localItems = { journalNotesRepository.allNotesObserved.first().associateBy { it.uid } },
                    idOf = { it.uid },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = noteConflictResolver,
                    hydrate = { token, note -> mediaTransfer.downloadIfNeeded(token, note) },
                    applyCreate = { note ->
                        if (syncableRepository != null) syncableRepository.createFromSync(note) else journalNotesRepository.create(note)
                    },
                    // Notes have no update-in-place sync path -- a replacement is always applied as
                    // remove-then-recreate, matching how a fresh download from the server is applied.
                    applyReplace = { existing, replacement ->
                        if (syncableRepository != null) {
                            syncableRepository.deleteFromSync(existing.uid)
                            syncableRepository.createFromSync(replacement)
                        } else {
                            journalNotesRepository.remove(existing)
                            journalNotesRepository.create(replacement)
                        }
                    },
                    applyDelete = { id ->
                        if (syncableRepository != null) syncableRepository.deleteFromSync(id) else journalNotesRepository.removeById(id)
                    },
                    afterDelete = { id -> mediaSyncRefStore.delete(id) },
                ),
            accessToken = accessToken,
            since = since,
        )
    }

    suspend fun downloadDrafts(
        accessToken: String,
        since: Instant,
    ): SyncResult =
        try {
            val pendingDrafts =
                syncMetadataService
                    .getPendingUploads(EntityType.DRAFT)
                    .map { it.entityId }
                    .toSet()
            val localDrafts =
                journalRepository
                    .getAllDrafts()
                    .associateBy { it.id }
                    .toMutableMap()
            val syncableDraftRepository = journalRepository as? SyncableDraftRepository

            var cursor = since
            var totalDownloaded = 0
            var totalConflicts = 0
            val errors = mutableListOf<SyncError>()

            while (true) {
                val result = cloudDraftDataSource.getDraftChanges(accessToken, cursor, SYNC_PAGE_SIZE).getOrThrow()

                val batchResult =
                    transactionManager.withTransaction {
                        var downloadedCount = 0
                        var conflictsResolved = 0
                        val batchErrors = mutableListOf<SyncError>()

                        for (remoteDraft in result.changes) {
                            try {
                                val existingDraft = localDrafts[remoteDraft.id]
                                val hasPendingLocal = pendingDrafts.contains(remoteDraft.id.toString())
                                if (hasPendingLocal) {
                                    conflictsResolved++
                                    Napier.w("Skipping draft update for ${remoteDraft.id} due to local pending changes")
                                    downloadEngine.recordConflict(
                                        entityType = EntityType.DRAFT,
                                        entityId = remoteDraft.id.toString(),
                                        reason = "Local pending draft changes vs remote update",
                                        localVersion = null,
                                        remoteVersion = remoteDraft.serverVersion,
                                        localUpdatedAt = existingDraft?.lastModifiedAt,
                                        remoteUpdatedAt = remoteDraft.lastUpdated,
                                    )
                                    continue
                                }

                                if (existingDraft != null && existingDraft.lastModifiedAt > remoteDraft.lastUpdated) {
                                    syncMetadataService.enqueuePending(
                                        entityId = existingDraft.id.toString(),
                                        entityType = EntityType.DRAFT,
                                        operation = PendingOperation.UPDATE,
                                    )
                                    conflictsResolved++
                                    Napier.w("Preserving newer local draft ${existingDraft.id} over older remote draft")
                                    continue
                                }

                                val draft = remoteDraft.toEditorDraft()
                                if (syncableDraftRepository != null) {
                                    syncableDraftRepository.saveDraftFromSync(draft)
                                } else {
                                    journalRepository.saveDraft(draft)
                                }
                                localDrafts[draft.id] = draft
                                downloadedCount++
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to apply draft change for ${remoteDraft.id}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to apply draft change for ${remoteDraft.id}", e)
                            }
                        }

                        for (draftId in result.deletions) {
                            try {
                                val existingDraft = localDrafts[draftId]
                                val hasPendingLocal =
                                    existingDraft != null &&
                                        pendingDrafts.contains(draftId.toString())
                                if (hasPendingLocal) {
                                    conflictsResolved++
                                    Napier.w("Skipping draft deletion for $draftId due to local pending changes")
                                    downloadEngine.recordConflict(
                                        entityType = EntityType.DRAFT,
                                        entityId = draftId.toString(),
                                        reason = "Local pending draft changes vs remote deletion",
                                        localVersion = null,
                                        remoteVersion = null,
                                        localUpdatedAt = existingDraft.lastModifiedAt,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableDraftRepository != null) {
                                    syncableDraftRepository.deleteDraftFromSync(draftId)
                                } else {
                                    journalRepository.deleteDraft(draftId)
                                }
                                localDrafts.remove(draftId)
                                downloadedCount++
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to delete draft $draftId: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to delete draft $draftId", e)
                            }
                        }

                        BatchResult(downloadedCount, conflictsResolved, batchErrors)
                    }

                totalDownloaded += batchResult.downloadedCount
                totalConflicts += batchResult.conflictsResolved
                errors.addAll(batchResult.errors)

                if (batchResult.errors.isNotEmpty()) {
                    break
                }

                if (result.changes.isEmpty() && result.deletions.isEmpty()) {
                    break
                }

                syncMetadataService.updateLastSyncTime(EntityType.DRAFT, result.lastSyncTimestamp)

                if (!result.hasMore) {
                    break
                }

                if (result.lastSyncTimestamp <= cursor) {
                    Napier.w("Draft sync pagination cursor did not advance (since=$cursor, last=${result.lastSyncTimestamp})")
                    break
                }

                cursor = result.lastSyncTimestamp
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = totalDownloaded,
                conflictsResolved = totalConflicts,
                errors = errors,
            )
        } catch (e: CloudApiException) {
            mapCloudApiError(e)
        } catch (e: Exception) {
            mapException(e, "Download drafts")
        }

    suspend fun downloadAssociations(
        accessToken: String,
        since: Instant,
    ): SyncResult =
        try {
            val syncableRepository = journalContentRepository as? SyncableJournalContentRepository
            val pendingAssociations =
                syncMetadataService
                    .getPendingUploads(EntityType.ASSOCIATION)
                    .map { it.entityId }
                    .toSet()

            var cursor = since
            var hasMore = true
            var totalDownloaded = 0
            var totalConflicts = 0
            val errors = mutableListOf<SyncError>()

            while (hasMore) {
                val result =
                    tokenRefresher
                        .withFreshToken(
                            { token -> cloudAssociationDataSource.getAssociationChanges(token, cursor, SYNC_PAGE_SIZE) },
                            "getAssociationChanges",
                        ).getOrThrow()

                val batchResult =
                    transactionManager.withTransaction {
                        var downloadedCount = 0
                        var conflictsResolved = 0
                        val batchErrors = mutableListOf<SyncError>()

                        for (association in result.additions) {
                            try {
                                val pendingKey = AssociationPendingKey(association.journalId, association.contentId).toPendingId()
                                if (pendingAssociations.contains(pendingKey)) {
                                    conflictsResolved++
                                    Napier.w("Skipping association add for $pendingKey due to local pending changes")
                                    downloadEngine.recordConflict(
                                        entityType = EntityType.ASSOCIATION,
                                        entityId = pendingKey,
                                        reason = "Local pending changes vs remote association add",
                                        localVersion = null,
                                        remoteVersion = association.syncVersion,
                                        localUpdatedAt = null,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableRepository != null) {
                                    syncableRepository.addContentToJournalFromSync(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                } else {
                                    journalContentRepository.addContentToJournal(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                }
                                downloadedCount++
                                Napier.d("Added association: journal ${association.journalId} -> content ${association.contentId}")
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to add association ${association.journalId}->${association.contentId}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to add association ${association.journalId}->${association.contentId}", e)
                            }
                        }

                        for (association in result.deletions) {
                            try {
                                val pendingKey = AssociationPendingKey(association.journalId, association.contentId).toPendingId()
                                if (pendingAssociations.contains(pendingKey)) {
                                    conflictsResolved++
                                    Napier.w("Skipping association delete for $pendingKey due to local pending changes")
                                    downloadEngine.recordConflict(
                                        entityType = EntityType.ASSOCIATION,
                                        entityId = pendingKey,
                                        reason = "Local pending changes vs remote association delete",
                                        localVersion = null,
                                        remoteVersion = null,
                                        localUpdatedAt = null,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableRepository != null) {
                                    syncableRepository.removeContentFromJournalFromSync(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                } else {
                                    journalContentRepository.removeContentFromJournal(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                }
                                downloadedCount++
                                Napier.d("Removed association: journal ${association.journalId} -> content ${association.contentId}")
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to remove association ${association.journalId}->${association.contentId}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to remove association ${association.journalId}->${association.contentId}", e)
                            }
                        }

                        BatchResult(downloadedCount, conflictsResolved, batchErrors)
                    }

                totalDownloaded += batchResult.downloadedCount
                totalConflicts += batchResult.conflictsResolved
                errors.addAll(batchResult.errors)

                if (batchResult.errors.isNotEmpty()) {
                    break
                }

                syncMetadataService.updateLastSyncTime(EntityType.ASSOCIATION, result.lastSyncTimestamp)

                if (!result.hasMore) {
                    break
                }

                if (result.lastSyncTimestamp <= cursor) {
                    Napier.w("Association sync pagination cursor did not advance (since=$cursor, last=${result.lastSyncTimestamp})")
                    break
                }

                cursor = result.lastSyncTimestamp
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = totalDownloaded,
                conflictsResolved = totalConflicts,
                errors = errors,
            )
        } catch (e: CloudApiException) {
            mapCloudApiError(e)
        } catch (e: Exception) {
            mapException(e, "Download associations")
        }

    private fun SyncedDraft.toEditorDraft(): EditorDraft =
        EditorDraft(
            id = id,
            blocks =
                content
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        listOf(
                            SerializableTextBlock(
                                id = id,
                                timestamp = createdAt,
                                content = it,
                            ),
                        )
                    }.orEmpty(),
            selectedJournalIds = journalIds,
            createdAt = createdAt,
            lastModifiedAt = lastUpdated,
        )

    private companion object {
        /**
         * Deliberately small. The server reads each record out of the account's repo, so the cost
         * of a page grows with the size of the page *and* the journal behind it; asking for 200 at
         * once stopped completing inside the request timeout once an account held a few hundred
         * entries, and a download that times out fails the whole sync. Raise this once a page is
         * cheap to serve again.
         */
        const val SYNC_PAGE_SIZE = 25
    }
}
