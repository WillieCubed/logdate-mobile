package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

/**
 * The concrete per-[EntityType] download side of sync: journals and notes fit
 * [SyncDownloadEngine]'s pluggable-[ConflictResolver] shape and are thin strategy definitions;
 * drafts (a simpler newer-wins heuristic) and associations (additions/deletions, not
 * changes/deletions) don't, and have their own hand-rolled paginate-apply loops instead, in
 * [DraftDownloader] and [AssociationDownloader].
 *
 * @param mapCloudApiError,mapException Reuse [DefaultSyncManager]'s own error mapping (including
 *   its `lastErrorFlow` side effect) instead of duplicating it here, so a download failure is
 *   reported identically to an upload failure.
 */
internal class SyncDownloader(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    journalContentRepository: JournalContentRepository,
    private val cloudJournalDataSource: CloudJournalDataSource,
    private val cloudContentDataSource: CloudContentDataSource,
    cloudDraftDataSource: CloudDraftDataSource,
    cloudAssociationDataSource: CloudAssociationDataSource,
    private val journalConflictResolver: ConflictResolver<Journal>,
    private val noteConflictResolver: ConflictResolver<JournalNote>,
    syncMetadataService: SyncMetadataService,
    transactionManager: SyncTransactionManager,
    private val mediaSyncRefStore: MediaSyncRefStore,
    private val downloadEngine: SyncDownloadEngine,
    private val mediaTransfer: SyncMediaTransfer,
    private val tokenRefresher: SyncTokenRefresher,
    mapCloudApiError: (CloudApiException) -> SyncResult,
    mapException: (Exception, String) -> SyncResult,
    private val downloadInbox: app.logdate.client.sync.recovery.DownloadInbox? = null,
) {
    private val draftDownloader =
        DraftDownloader(
            journalRepository = journalRepository,
            cloudDraftDataSource = cloudDraftDataSource,
            syncMetadataService = syncMetadataService,
            transactionManager = transactionManager,
            downloadEngine = downloadEngine,
            tokenRefresher = tokenRefresher,
            mapCloudApiError = mapCloudApiError,
            mapException = mapException,
            downloadInbox = downloadInbox,
        )

    private val associationDownloader =
        AssociationDownloader(
            journalContentRepository = journalContentRepository,
            cloudAssociationDataSource = cloudAssociationDataSource,
            syncMetadataService = syncMetadataService,
            transactionManager = transactionManager,
            downloadEngine = downloadEngine,
            tokenRefresher = tokenRefresher,
            mapCloudApiError = mapCloudApiError,
            mapException = mapException,
            downloadInbox = downloadInbox,
        )

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
                            ).map {
                                ChangesPage(
                                    it.changes,
                                    it.deletions,
                                    it.lastSyncTimestamp,
                                    it.hasMore,
                                    it.unreadable,
                                    it.failures,
                                    it.unreadableVersions,
                                )
                            }
                    },
                    localItems = { journalRepository.allJournalsObserved.first().associateBy { it.id } },
                    localItem = journalRepository::getJournalById,
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
                            ).map {
                                ChangesPage(
                                    it.changes,
                                    it.deletions,
                                    it.lastSyncTimestamp,
                                    it.hasMore,
                                    it.unreadable,
                                    it.failures,
                                    it.unreadableVersions,
                                )
                            }
                    },
                    localItems = { journalNotesRepository.allNotesObserved.first().associateBy { it.uid } },
                    localItem = journalNotesRepository::getNoteById,
                    idOf = { it.uid },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = noteConflictResolver,
                    hydrate = { token, note -> if (downloadInbox == null) mediaTransfer.downloadIfNeeded(token, note) else note },
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
    ): SyncResult = draftDownloader.download(since)

    suspend fun downloadAssociations(
        accessToken: String,
        since: Instant,
    ): SyncResult = associationDownloader.download(since)

    companion object {
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
