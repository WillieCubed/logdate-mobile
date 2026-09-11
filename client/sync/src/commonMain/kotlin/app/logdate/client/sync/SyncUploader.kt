package app.logdate.client.sync

import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.shouldSyncMedia
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.JournalContentAssociation
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.sync.DeviceId
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * The concrete per-[EntityType] upload side of sync: reads whatever's pending from
 * [syncMetadataService], uploads or deletes it through the matching cloud data source (with a
 * media pre-upload step for notes), and settles or retries each attempt through
 * [SyncRetryCoordinator].
 *
 * @param recordProgress,setMediaDeferredForNetwork Report into [DefaultSyncManager]'s own run
 *   progress and paused-reason state -- narrow callbacks rather than a reference back to the
 *   manager itself, since uploading has no other reason to reach into it.
 * @param mapCloudApiError,mapException Reuse [DefaultSyncManager]'s own error mapping (including
 *   its `lastErrorFlow` side effect) instead of duplicating it here, so an upload failure is
 *   reported identically to a download failure.
 */
internal class SyncUploader(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val cloudJournalDataSource: CloudJournalDataSource,
    private val cloudContentDataSource: CloudContentDataSource,
    private val cloudAssociationDataSource: CloudAssociationDataSource,
    private val cloudDraftDataSource: CloudDraftDataSource,
    private val mediaSyncRefStore: MediaSyncRefStore,
    private val syncMetadataService: SyncMetadataService,
    private val dataUsagePolicy: DataUsagePolicy,
    private val deviceIdProvider: DeviceIdProvider?,
    private val tokenRefresher: SyncTokenRefresher,
    private val mediaTransfer: SyncMediaTransfer,
    private val retryCoordinator: SyncRetryCoordinator,
    private val mapCloudApiError: (CloudApiException) -> SyncResult,
    private val mapException: (Exception, String) -> SyncResult,
    private val recordProgress: (Int) -> Unit,
    private val setMediaDeferredForNetwork: (Boolean) -> Unit,
) {
    /**
     * Drops entries still inside their retry backoff before callers decide whether there is any
     * work. Dead-lettered entries stay queued indefinitely, so without this an entry that can
     * never upload keeps every sync run loading a whole table to do nothing with.
     */
    private suspend fun List<PendingUpload>.dueNow(entityType: EntityType): List<PendingUpload> =
        filter { retryCoordinator.shouldAttempt(entityType, it.entityId) }

    suspend fun uploadJournals(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.JOURNAL).dueNow(EntityType.JOURNAL)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val syncableRepository = journalRepository as? SyncableJournalRepository
            val journalsById =
                journalRepository.allJournalsObserved
                    .first()
                    .associateBy { it.id.toString() }

            for (pending in pendingUploads) {
                val journalId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (journalId == null) {
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.JOURNAL, pending.entityId, "journal ID"))
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudJournalDataSource.deleteJournal(token, journalId) },
                                "deleteJournal($journalId)",
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            recordProgress(1)
                            retryCoordinator.markUploadSettled(EntityType.JOURNAL, pending.entityId, Clock.System.now(), 0L)
                            Napier.d("Successfully deleted journal: $journalId")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                            val movedToDeadLetter =
                                retryCoordinator.handleRetryFailure(
                                    entityType = EntityType.JOURNAL,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete journal $journalId: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to delete journal $journalId", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        val journal = journalsById[pending.entityId]
                        if (journal == null) {
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.JOURNAL,
                                Clock.System.now(),
                                0L,
                            )
                            continue
                        }

                        val result =
                            if (pending.operation == PendingOperation.CREATE) {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudJournalDataSource.uploadJournal(token, journal) },
                                    "uploadJournal(${journal.id})",
                                )
                            } else {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudJournalDataSource.updateJournal(token, journal) },
                                    "updateJournal(${journal.id})",
                                )
                            }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            recordProgress(1)
                            syncableRepository?.updateSyncMetadata(journalId, upload.serverVersion, upload.syncedAt)
                            retryCoordinator.markUploadSettled(EntityType.JOURNAL, pending.entityId, upload.syncedAt, upload.serverVersion)
                            Napier.d("Successfully uploaded journal: ${journal.id}")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            if ((error as? CloudApiException)?.statusCode == 409) {
                                errors.add(
                                    retryCoordinator.handleUploadConflict(
                                        entityType = EntityType.JOURNAL,
                                        entityId = journal.id.toString(),
                                        itemLabel = "journal ${journal.id}",
                                        conflictLabel = "Journal",
                                        error = error,
                                        localVersion = journal.syncVersion,
                                        localUpdatedAt = journal.lastUpdated,
                                    ),
                                )
                                continue
                            }
                            val movedToDeadLetter =
                                retryCoordinator.handleRetryFailure(
                                    entityType = EntityType.JOURNAL,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    if ((error as? CloudApiException)?.statusCode == 409) {
                                        SyncErrorType.CONFLICT_ERROR
                                    } else {
                                        SyncErrorType.SERVER_ERROR
                                    },
                                    "Failed to upload journal ${journal.id}: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to upload journal ${journal.id}", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            mapCloudApiError(e)
        } catch (e: Exception) {
            mapException(e, "Upload journals")
        }
    }

    suspend fun uploadContent(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            setMediaDeferredForNetwork(false)

            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.NOTE).dueNow(EntityType.NOTE)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val syncableRepository = journalNotesRepository as? SyncableJournalNotesRepository
            val notesById =
                journalNotesRepository.allNotesObserved
                    .first()
                    .associateBy { it.uid.toString() }

            for (pending in pendingUploads) {
                val noteId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (noteId == null) {
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.NOTE, pending.entityId, "note ID"))
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudContentDataSource.deleteNote(token, noteId) },
                                "deleteNote($noteId)",
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            recordProgress(1)
                            // Deliberately before markUploadSettled: if this throws, the item must
                            // stay pending so the next attempt retries the ref-store cleanup too,
                            // rather than being marked settled with a stale mediaSyncRefStore entry
                            // that nothing will ever clean up again.
                            mediaSyncRefStore.delete(noteId)
                            retryCoordinator.markUploadSettled(EntityType.NOTE, pending.entityId, Clock.System.now(), 0L)
                            Napier.d("Successfully deleted content: $noteId")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                            val movedToDeadLetter =
                                retryCoordinator.handleRetryFailure(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete content $noteId: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to delete content $noteId", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        val note = notesById[pending.entityId]
                        if (note == null) {
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.NOTE,
                                Clock.System.now(),
                                0L,
                            )
                            continue
                        }
                        val mediaRef = note.mediaRefOrNull()
                        val uploadReadyNote =
                            if (mediaRef != null && !mediaTransfer.isRemoteRef(mediaRef)) {
                                if (!dataUsagePolicy.currentMode().shouldSyncMedia()) {
                                    setMediaDeferredForNetwork(true)
                                    Napier.d("Deferring media upload for note ${note.uid} — data usage policy restricts media sync")
                                    continue
                                } else {
                                    val mediaUpload = mediaTransfer.uploadIfNeeded(accessToken, note)
                                    if (mediaUpload.isFailure) {
                                        val error =
                                            mediaUpload.exceptionOrNull()
                                                ?: Exception("Unknown media upload error")
                                        // This used to `continue` without recording an attempt, so a
                                        // note whose media file no longer exists on disk retried for
                                        // ever and held the whole queue behind it - the upload can
                                        // never succeed, because the bytes are gone. Counting the
                                        // attempt lets it dead-letter like any other stuck upload,
                                        // where it dead-letters silently -- there is currently no UI
                                        // surfacing dead-lettered items for review.
                                        //
                                        // A single existence check isn't proof of that, though --
                                        // MediaManager.exists() answers false for a provider that
                                        // merely failed to answer, not only for bytes truly gone
                                        // (see its doc comment). Only treat this as permanent once
                                        // the *immediately preceding* attempt for this same note was
                                        // also a missing-media miss -- not merely once its generic,
                                        // shared retryCount happens to be >= 1, which an unrelated
                                        // failure (network, server, ...) could have put there -- so
                                        // one bad read doesn't wrongly bury a file that's still there.
                                        val movedToDeadLetter =
                                            retryCoordinator.handleRetryFailure(
                                                entityType = EntityType.NOTE,
                                                pending = pending,
                                                error = error,
                                                permanent =
                                                    error is MissingMediaException &&
                                                        retryCoordinator.previousFailureWasMissingMedia(EntityType.NOTE, pending.entityId),
                                            )
                                        errors.add(
                                            SyncError(
                                                SyncErrorType.STORAGE_ERROR,
                                                "Failed to upload media for note ${note.uid}: ${error.message}",
                                                error,
                                                retryable = !movedToDeadLetter,
                                            ),
                                        )
                                        Napier.w("Skipping note ${note.uid} sync; media upload failed", error)
                                        continue
                                    }
                                    mediaUpload.getOrThrow()
                                }
                            } else {
                                note
                            }

                        val result =
                            if (pending.operation == PendingOperation.CREATE) {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudContentDataSource.uploadNote(token, uploadReadyNote) },
                                    "uploadNote(${note.uid})",
                                )
                            } else {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudContentDataSource.updateNote(token, uploadReadyNote) },
                                    "updateNote(${note.uid})",
                                )
                            }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            recordProgress(1)
                            syncableRepository?.updateSyncMetadata(note, upload.serverVersion, upload.syncedAt)
                            retryCoordinator.markUploadSettled(EntityType.NOTE, pending.entityId, upload.syncedAt, upload.serverVersion)
                            Napier.d("Successfully uploaded content: ${note.uid}")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            if ((error as? CloudApiException)?.statusCode == 409) {
                                errors.add(
                                    retryCoordinator.handleUploadConflict(
                                        entityType = EntityType.NOTE,
                                        entityId = note.uid.toString(),
                                        itemLabel = "content ${note.uid}",
                                        conflictLabel = "Content",
                                        error = error,
                                        localVersion = note.syncVersion,
                                        localUpdatedAt = note.lastUpdated,
                                    ),
                                )
                                continue
                            }
                            val movedToDeadLetter =
                                retryCoordinator.handleRetryFailure(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    if ((error as? CloudApiException)?.statusCode == 409) {
                                        SyncErrorType.CONFLICT_ERROR
                                    } else {
                                        SyncErrorType.SERVER_ERROR
                                    },
                                    "Failed to upload content ${note.uid}: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to upload content ${note.uid}", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            mapCloudApiError(e)
        } catch (e: Exception) {
            mapException(e, "Upload content")
        }
    }

    suspend fun uploadAssociations(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()

            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.ASSOCIATION)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val pendingById = pendingUploads.associateBy { it.entityId }
            val createAssociations = mutableListOf<JournalContentAssociation>()
            val createIds = mutableListOf<String>()
            val deleteAssociations = mutableListOf<JournalContentAssociation>()
            val deleteIds = mutableListOf<String>()

            pendingUploads.forEach { pending ->
                if (!retryCoordinator.shouldAttempt(EntityType.ASSOCIATION, pending.entityId)) {
                    return@forEach
                }
                val key = AssociationPendingKey.fromPendingId(pending.entityId)
                if (key == null) {
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.ASSOCIATION, pending.entityId, "association key"))
                    return@forEach
                }

                val association =
                    JournalContentAssociation(
                        journalId = key.journalId,
                        contentId = key.contentId,
                        createdAt = Clock.System.now(),
                    )

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        deleteAssociations.add(association)
                        deleteIds.add(pending.entityId)
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        createAssociations.add(association)
                        createIds.add(pending.entityId)
                    }
                }
            }

            if (createAssociations.isNotEmpty()) {
                val result =
                    tokenRefresher.withFreshToken(
                        { token -> cloudAssociationDataSource.uploadAssociations(token, createAssociations) },
                        "uploadAssociations(${createAssociations.size} items)",
                    )
                if (result.isSuccess) {
                    val uploadedAt = result.getOrThrow()
                    createIds.forEach { id -> retryCoordinator.markUploadSettled(EntityType.ASSOCIATION, id, uploadedAt, 0L) }
                    uploadedCount += createAssociations.size
                    recordProgress(createAssociations.size)
                    Napier.d("Successfully uploaded associations: ${createAssociations.size}")
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                    var movedToDeadLetter = false
                    createIds.forEach { id ->
                        val pending = pendingById[id] ?: return@forEach
                        if (retryCoordinator.handleRetryFailure(EntityType.ASSOCIATION, pending, error)) {
                            movedToDeadLetter = true
                        }
                    }
                    errors.add(
                        SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to upload associations: ${error.message}",
                            error,
                            retryable = !movedToDeadLetter,
                        ),
                    )
                    Napier.w("Failed to upload associations", error)
                }
            }

            if (deleteAssociations.isNotEmpty()) {
                val result =
                    tokenRefresher.withFreshToken(
                        { token -> cloudAssociationDataSource.deleteAssociations(token, deleteAssociations) },
                        "deleteAssociations(${deleteAssociations.size} items)",
                    )
                if (result.isSuccess) {
                    val deletedAt = Clock.System.now()
                    deleteIds.forEach { id -> retryCoordinator.markUploadSettled(EntityType.ASSOCIATION, id, deletedAt, 0L) }
                    uploadedCount += deleteAssociations.size
                    recordProgress(deleteAssociations.size)
                    Napier.d("Successfully deleted associations: ${deleteAssociations.size}")
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                    var movedToDeadLetter = false
                    deleteIds.forEach { id ->
                        val pending = pendingById[id] ?: return@forEach
                        if (retryCoordinator.handleRetryFailure(EntityType.ASSOCIATION, pending, error)) {
                            movedToDeadLetter = true
                        }
                    }
                    errors.add(
                        SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to delete associations: ${error.message}",
                            error,
                            retryable = !movedToDeadLetter,
                        ),
                    )
                    Napier.w("Failed to delete associations", error)
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            mapCloudApiError(e)
        } catch (e: Exception) {
            mapException(e, "Upload associations")
        }
    }

    suspend fun uploadDrafts(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.DRAFT).dueNow(EntityType.DRAFT)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val draftsById = journalRepository.getAllDrafts().associateBy { it.id.toString() }
            val deviceId = currentDeviceId()

            for (pending in pendingUploads) {
                val draftId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (draftId == null) {
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.DRAFT, pending.entityId, "draft ID"))
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudDraftDataSource.deleteDraft(token, draftId) },
                                "deleteDraft($draftId)",
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            recordProgress(1)
                            retryCoordinator.markUploadSettled(EntityType.DRAFT, pending.entityId, Clock.System.now(), 0L)
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown draft delete error")
                            val movedToDeadLetter = retryCoordinator.handleRetryFailure(EntityType.DRAFT, pending, error)
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete draft $draftId: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        val draft = draftsById[pending.entityId]
                        if (draft == null) {
                            syncMetadataService.markAsSynced(pending.entityId, EntityType.DRAFT, Clock.System.now(), 0L)
                            continue
                        }

                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudDraftDataSource.uploadDraft(token, draft, deviceId) },
                                "uploadDraft(${draft.id})",
                            )
                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            recordProgress(1)
                            retryCoordinator.markUploadSettled(EntityType.DRAFT, pending.entityId, upload.syncedAt, upload.serverVersion)
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown draft upload error")
                            val movedToDeadLetter = retryCoordinator.handleRetryFailure(EntityType.DRAFT, pending, error)
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to upload draft ${draft.id}: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            mapCloudApiError(e)
        } catch (e: Exception) {
            mapException(e, "Upload drafts")
        }
    }

    private fun currentDeviceId(): DeviceId =
        deviceIdProvider
            ?.getDeviceId()
            ?.value
            ?.toString()
            ?.let(::DeviceId)
            ?: DeviceId.UNKNOWN

    /**
     * Queues everything already on this device the first time it syncs with a server.
     *
     * Entries can exist before the device has ever talked to a server: written offline, or restored
     * from a backup. Nothing enqueues those retrospectively, so without this they sit on the device
     * for ever while sync reports success and uploads nothing. Signing in on a second device
     * promises exactly this, and it has to be true.
     *
     * Only runs while the server has never been synced with, and enqueueing coalesces, so an entry
     * already waiting is not queued twice.
     */
    suspend fun enqueueEverythingOnFirstSync() {
        val entityTypes = listOf(EntityType.JOURNAL, EntityType.NOTE)
        val neverSynced = entityTypes.filter { syncMetadataService.getLastSyncTime(it) == null }
        if (neverSynced.isEmpty()) {
            return
        }

        runCatching {
            if (EntityType.JOURNAL in neverSynced) {
                val journals = journalRepository.allJournalsObserved.first()
                journals.forEach { journal ->
                    syncMetadataService.enqueuePending(
                        entityId = journal.id.toString(),
                        entityType = EntityType.JOURNAL,
                        operation = PendingOperation.CREATE,
                    )
                }
                Napier.i("First sync: queued ${journals.size} journals already on this device")
            }
            if (EntityType.NOTE in neverSynced) {
                val notes = journalNotesRepository.allNotesObserved.first()
                notes.forEach { note ->
                    syncMetadataService.enqueuePending(
                        entityId = note.uid.toString(),
                        entityType = EntityType.NOTE,
                        operation = PendingOperation.CREATE,
                    )
                }
                Napier.i("First sync: queued ${notes.size} entries already on this device")
            }
        }.onFailure { error ->
            Napier.w("Could not queue existing entries for the first sync", error)
        }
    }
}
