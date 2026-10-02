package app.logdate.client.sync

import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.shouldSyncMedia
import app.logdate.client.repository.journals.JournalNote
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
import app.logdate.client.sync.cloud.MediaTooLargeException
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.FirstSyncEnqueueStore
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.recovery.DownloadScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException
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
    cloudAssociationDataSource: CloudAssociationDataSource,
    cloudDraftDataSource: CloudDraftDataSource,
    private val mediaSyncRefStore: MediaSyncRefStore,
    private val syncMetadataService: SyncMetadataService,
    private val dataUsagePolicy: DataUsagePolicy,
    deviceIdProvider: DeviceIdProvider?,
    private val tokenRefresher: SyncTokenRefresher,
    private val mediaTransfer: SyncMediaTransfer,
    private val retryCoordinator: SyncRetryCoordinator,
    private val firstSyncEnqueueStore: FirstSyncEnqueueStore,
    private val mapCloudApiError: (CloudApiException) -> SyncResult,
    private val mapException: (Exception, String) -> SyncResult,
    private val recordProgress: (Int) -> Unit,
    private val setMediaDeferredForNetwork: (Boolean) -> Unit,
    supportsRichDrafts: () -> Boolean = { false },
    draftRepairScope: () -> DownloadScope? = { null },
) {
    private val associationUploader =
        AssociationUploader(
            cloudAssociationDataSource = cloudAssociationDataSource,
            syncMetadataService = syncMetadataService,
            tokenRefresher = tokenRefresher,
            retryCoordinator = retryCoordinator,
            recordProgress = recordProgress,
        )

    private val draftUploader =
        DraftUploader(
            journalRepository = journalRepository,
            cloudDraftDataSource = cloudDraftDataSource,
            syncMetadataService = syncMetadataService,
            deviceIdProvider = deviceIdProvider,
            tokenRefresher = tokenRefresher,
            mediaTransfer = mediaTransfer,
            retryCoordinator = retryCoordinator,
            firstSyncEnqueueStore = firstSyncEnqueueStore,
            recordProgress = recordProgress,
            supportsRichDrafts = supportsRichDrafts,
            draftRepairScope = draftRepairScope,
        )

    /**
     * Runs one upload pass, reporting an unexpected failure as a [SyncResult] for [operation]. A pass
     * that is stopped or fails lets go of the attempts it started: only the app closing may leave
     * one unfinished, so nothing else counts as that against the entries it was uploading.
     */
    private suspend inline fun uploadPass(
        operation: String,
        pass: () -> SyncResult,
    ): SyncResult =
        try {
            pass()
        } catch (e: Exception) {
            retryCoordinator.abandonAttemptsInFlight()
            when (e) {
                is CancellationException -> throw e
                is CloudApiException -> mapCloudApiError(e)
                else -> mapException(e, operation)
            }
        }

    suspend fun uploadJournals(accessToken: String): SyncResult {
        return uploadPass("Upload journals") {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.JOURNAL).dueNow(EntityType.JOURNAL, retryCoordinator)
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
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.JOURNAL, pending, "journal ID"))
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        if (!retryCoordinator.beginAttempt(EntityType.JOURNAL, pending, errors)) continue
                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudJournalDataSource.deleteJournal(token, journalId) },
                                "deleteJournal($journalId)",
                                expectedScope = pending.scope,
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            recordProgress(1)
                            retryCoordinator.markUploadSettled(EntityType.JOURNAL, pending, Clock.System.now(), 0L)
                            Napier.d("Deleted journal")
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
                            Napier.w("Failed to delete journal")
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
                            // Queued for upload but absent locally. Settling it is right -- there is
                            // nothing left to send -- but it must not happen silently: the entry
                            // never reached the server and this is the only trace it existed.
                            Napier.w("Dropping queued journal: no longer present locally")
                            retryCoordinator.markUploadSettled(
                                EntityType.JOURNAL,
                                pending,
                                Clock.System.now(),
                                0L,
                            )
                            continue
                        }

                        if (!retryCoordinator.beginAttempt(EntityType.JOURNAL, pending, errors)) continue
                        val result =
                            if (pending.operation == PendingOperation.CREATE) {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudJournalDataSource.uploadJournal(token, journal) },
                                    "uploadJournal(${journal.id})",
                                    expectedScope = pending.scope,
                                )
                            } else {
                                tokenRefresher.withFreshToken(
                                    { token ->
                                        cloudJournalDataSource.updateJournal(
                                            token,
                                            journal.copy(
                                                syncVersion =
                                                    pending.expectedServerVersion ?: journal.syncVersion,
                                            ),
                                        )
                                    },
                                    "updateJournal(${journal.id})",
                                    expectedScope = pending.scope,
                                )
                            }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            recordProgress(1)
                            syncableRepository?.updateSyncMetadata(journalId, upload.serverVersion, upload.syncedAt)
                            retryCoordinator.markUploadSettled(EntityType.JOURNAL, pending, upload.syncedAt, upload.serverVersion)
                            Napier.d("Uploaded journal")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            if ((error as? CloudApiException)?.statusCode == 409) {
                                errors.add(
                                    retryCoordinator.handleUploadConflict(
                                        entityType = EntityType.JOURNAL,
                                        pending = pending,
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
                            Napier.w("Failed to upload journal")
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        }
    }

    suspend fun uploadContent(accessToken: String): SyncResult {
        return uploadPass("Upload content") {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            setMediaDeferredForNetwork(false)

            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.NOTE).dueNow(EntityType.NOTE, retryCoordinator)
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
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.NOTE, pending, "note ID"))
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        if (!retryCoordinator.beginAttempt(EntityType.NOTE, pending, errors)) continue
                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudContentDataSource.deleteNote(token, noteId) },
                                "deleteNote($noteId)",
                                expectedScope = pending.scope,
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            recordProgress(1)
                            // Deliberately before markUploadSettled: if this throws, the item must
                            // stay pending so the next attempt retries the ref-store cleanup too,
                            // rather than being marked settled with a stale mediaSyncRefStore entry
                            // that nothing will ever clean up again.
                            mediaSyncRefStore.deleteScoped(noteId, pending.scope)
                            retryCoordinator.markUploadSettled(EntityType.NOTE, pending, Clock.System.now(), 0L)
                            Napier.d("Deleted content")
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
                            Napier.w("Failed to delete content")
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
                            Napier.w("Dropping queued note: no longer present locally")
                            retryCoordinator.markUploadSettled(
                                EntityType.NOTE,
                                pending,
                                Clock.System.now(),
                                0L,
                            )
                            continue
                        }
                        val mediaRef = note.mediaRefOrNull()
                        val needsMediaUpload = mediaRef != null && !mediaTransfer.isRemoteRef(mediaRef)
                        if (needsMediaUpload && !dataUsagePolicy.currentMode().shouldSyncMedia()) {
                            setMediaDeferredForNetwork(true)
                            Napier.d("Deferring media upload for note — data usage policy restricts media sync")
                            continue
                        }

                        if (!retryCoordinator.beginAttempt(EntityType.NOTE, pending, errors)) continue
                        val uploadReadyNote =
                            if (needsMediaUpload) {
                                val mediaUpload =
                                    tokenRefresher.withFreshToken(
                                        { token -> mediaTransfer.uploadIfNeeded(token, note) },
                                        "uploadNoteMedia",
                                        expectedScope = pending.scope,
                                    )
                                if (mediaUpload.isFailure) {
                                    val error =
                                        mediaUpload.exceptionOrNull()
                                            ?: Exception("Unknown media upload error")
                                    // This used to `continue` without recording an attempt, so a
                                    // note whose media file no longer exists on disk retried for
                                    // ever and held the whole queue behind it - the upload can
                                    // never succeed, because the bytes are gone. Counting the
                                    // attempt lets it dead-letter into Sync Issues like any other
                                    // stuck upload.
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
                                                error is MediaTooLargeException ||
                                                    (
                                                        error is MissingMediaException &&
                                                            retryCoordinator.previousFailureWasMissingMedia(
                                                                EntityType.NOTE,
                                                                pending,
                                                            )
                                                    ),
                                        )
                                    errors.add(
                                        SyncError(
                                            // STORAGE_ERROR renders as "Cloud storage is full",
                                            // which is a lie for a file missing from this device
                                            // and sends the user to a billing page for a local
                                            // problem. Only a real server-side storage refusal
                                            // earns that message.
                                            if (error is MissingMediaException) {
                                                SyncErrorType.UNKNOWN_ERROR
                                            } else {
                                                SyncErrorType.STORAGE_ERROR
                                            },
                                            "Failed to upload media for note ${note.uid}: ${error.message}",
                                            error,
                                            retryable = !movedToDeadLetter,
                                        ),
                                    )
                                    Napier.w("Skipping note sync; media upload failed")
                                    continue
                                }
                                mediaUpload.getOrThrow()
                            } else {
                                note
                            }

                        val result =
                            if (pending.operation == PendingOperation.CREATE) {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudContentDataSource.uploadNote(token, uploadReadyNote) },
                                    "uploadNote(${note.uid})",
                                    expectedScope = pending.scope,
                                )
                            } else {
                                tokenRefresher.withFreshToken(
                                    { token ->
                                        cloudContentDataSource.updateNote(
                                            token,
                                            uploadReadyNote.withRepairVersion(pending.expectedServerVersion),
                                        )
                                    },
                                    "updateNote(${note.uid})",
                                    expectedScope = pending.scope,
                                )
                            }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            recordProgress(1)
                            syncableRepository?.updateSyncMetadata(note, upload.serverVersion, upload.syncedAt)
                            retryCoordinator.markUploadSettled(EntityType.NOTE, pending, upload.syncedAt, upload.serverVersion)
                            Napier.d("Uploaded content")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            if ((error as? CloudApiException)?.statusCode == 409) {
                                errors.add(
                                    retryCoordinator.handleUploadConflict(
                                        entityType = EntityType.NOTE,
                                        pending = pending,
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
                            Napier.w("Failed to upload content")
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        }
    }

    suspend fun uploadAssociations(accessToken: String): SyncResult =
        uploadPass("Upload associations") { associationUploader.uploadPending() }

    suspend fun uploadDrafts(accessToken: String): SyncResult = uploadPass("Upload drafts") { draftUploader.uploadPending() }

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
     *
     * "Never synced" itself is checked two ways: [firstSyncEnqueueStore] records whether this sweep
     * has ever completed for the entity type, independent of [syncMetadataService]'s download
     * cursor. The cursor alone isn't enough -- if downloading keeps failing, the cursor never
     * advances, and without the store's own flag this whole-table scan would repeat on every full
     * sync attempt for ever, rather than the one time it is meant to run.
     */
    suspend fun enqueueEverythingOnFirstSync() {
        val entityTypes = listOf(EntityType.JOURNAL, EntityType.NOTE)
        val neverSynced =
            entityTypes.filter { entityType ->
                !firstSyncEnqueueStore.hasEnqueued(entityType) && syncMetadataService.getLastSyncTime(entityType) == null
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
                // Marked only once the loop above has fully succeeded -- if it throws partway,
                // this line never runs and the next attempt retries the whole type from scratch.
                firstSyncEnqueueStore.markEnqueued(EntityType.JOURNAL)
                Napier.i("First sync: queued journals already on this device")
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
                firstSyncEnqueueStore.markEnqueued(EntityType.NOTE)
                Napier.i("First sync: queued entries already on this device")
            }
            enqueueDraftsForRichSync()
        }.onFailure { error ->
            Napier.w("Could not queue existing entries for the first sync")
        }
    }

    /** Queues pre-existing drafts once per signed-in owner/server, preserving pending deletions. */
    suspend fun enqueueDraftsForRichSync() = draftUploader.enqueueForRichSync()
}

/**
 * Drops entries still inside their retry backoff before callers decide whether there is any
 * work. Dead-lettered entries stay queued indefinitely, so without this an entry that can
 * never upload keeps every sync run loading a whole table to do nothing with.
 */
internal suspend fun List<PendingUpload>.dueNow(
    entityType: EntityType,
    retryCoordinator: SyncRetryCoordinator,
): List<PendingUpload> = filter { retryCoordinator.shouldAttempt(entityType, it) }

/**
 * Records that an upload of [pending] is starting, before anything that could take the app down with
 * it. Returns false when earlier attempts never finished and the entry has been set aside instead,
 * with the reason added to [errors].
 */
internal suspend fun SyncRetryCoordinator.beginAttempt(
    entityType: EntityType,
    pending: PendingUpload,
    errors: MutableList<SyncError>,
): Boolean {
    val setAside = beginUpload(entityType, pending) ?: return true
    errors.add(setAside)
    return false
}

private fun JournalNote.withRepairVersion(version: Long?): JournalNote =
    if (version == null) {
        this
    } else {
        when (this) {
            is JournalNote.Text -> copy(syncVersion = version)
            is JournalNote.Image -> copy(syncVersion = version)
            is JournalNote.Video -> copy(syncVersion = version)
            is JournalNote.Audio -> copy(syncVersion = version)
        }
    }
