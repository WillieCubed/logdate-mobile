package app.logdate.client.sync

import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.ContentState
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.FirstSyncEnqueueStore
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.space.SpaceKind
import app.logdate.client.sync.space.SpaceMediaReference
import app.logdate.client.sync.space.SpaceSync
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
    private val profile: SyncProfile = SyncProfile.Standard,
    createCollisionPolicy: CreateCollisionPolicy = CreateCollisionPolicy.None,
    private val transactionManager: SyncTransactionManager,
    private val space: SpaceSync? = null,
) {
    private val associationUploader =
        AssociationUploader(
            cloudAssociationDataSource = cloudAssociationDataSource,
            journalRepository = journalRepository,
            syncMetadataService = syncMetadataService,
            tokenRefresher = tokenRefresher,
            retryCoordinator = retryCoordinator,
            recordProgress = recordProgress,
            createsIfAbsent = profile.createsConditionally,
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

    private val journalUploader =
        JournalUploader(
            journalRepository,
            cloudJournalDataSource,
            syncMetadataService,
            tokenRefresher,
            retryCoordinator,
            recordProgress,
            profile,
        )
    private val collisions =
        CreateCollisionSettler(createCollisionPolicy, retryCoordinator, syncMetadataService, recordProgress, transactionManager)
    private val noteMediaUpload =
        NoteMediaUpload(dataUsagePolicy, tokenRefresher, mediaTransfer, retryCoordinator, setMediaDeferredForNetwork)

    private val conditionalCreates = ConditionalCreateSettler(retryCoordinator, recordProgress)

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

    suspend fun uploadJournals(accessToken: String): SyncResult =
        uploadPass("Upload journals") {
            val spaceKind = space?.activeKind(EntityType.JOURNAL)
            if (spaceKind != null) space.uploadKind(spaceKind, profile.uploadsUpdates) else journalUploader.uploadPending()
        }

    /** Notes in the personal space: their media is uploaded first, as a space blob, exactly as [uploadContent] does. */
    private suspend fun uploadNotesToSpace(
        space: SpaceSync,
        kind: SpaceKind<*>,
    ): SyncResult {
        @Suppress("UNCHECKED_CAST")
        val notes = kind as SpaceKind<JournalNote>
        return space.upload(
            notes,
            profile.uploadsUpdates,
            // A file uploaded to the per-kind endpoint before the switch is uploaded again, into the space.
            begin = { pending, note, errors -> noteMediaUpload.prepare(pending, note, errors) { SpaceMediaReference.parse(it) != null } },
            afterDelete = { pending -> Uuid.parseOrNull(pending.entityId)?.let { mediaSyncRefStore.deleteScoped(it, pending.scope) } },
        )
    }

    suspend fun uploadContent(accessToken: String): SyncResult {
        return uploadPass("Upload content") {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            setMediaDeferredForNetwork(false)
            val spaceKind = space?.activeKind(EntityType.NOTE)
            if (spaceKind != null) return uploadNotesToSpace(space, spaceKind)

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
                        if (pending.operation == PendingOperation.UPDATE && !profile.uploadsUpdates) {
                            Napier.d("Leaving an entry edit for the phone to relay")
                            continue
                        }
                        val note = notesById[pending.entityId]
                        if (note == null) {
                            errors.add(retryCoordinator.recordUnavailableOutboxEntry(EntityType.NOTE, pending))
                            continue
                        }
                        val uploadReadyNote = noteMediaUpload.prepare(pending, note, errors) ?: continue

                        if (pending.operation == PendingOperation.CREATE && profile.createsConditionally) {
                            val created =
                                tokenRefresher.withFreshToken(
                                    { token -> cloudContentDataSource.createNoteIfAbsent(token, uploadReadyNote) },
                                    "createNoteIfAbsent(${note.uid})",
                                    expectedScope = pending.scope,
                                )
                            uploadedCount +=
                                conditionalCreates.settle(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    result = created,
                                    errors = errors,
                                    onCreated = { upload ->
                                        syncableRepository?.updateSyncMetadata(note, upload.serverVersion, upload.syncedAt)
                                    },
                                    onDeleted = {
                                        if (syncableRepository !=
                                            null
                                        ) {
                                            syncableRepository.deleteFromSync(noteId)
                                        } else {
                                            journalNotesRepository.removeById(noteId)
                                        }
                                        mediaSyncRefStore.deleteScoped(noteId, pending.scope)
                                    },
                                )
                            continue
                        }

                        val result =
                            if (pending.operation == PendingOperation.CREATE && note.syncVersion == 0L) {
                                if (collisions.isKnownCapture(EntityType.NOTE, pending.entityId)) {
                                    tokenRefresher
                                        .withFreshToken(
                                            { token -> cloudContentDataSource.createNoteIfAbsent(token, uploadReadyNote) },
                                            "createWatchCaptureIfAbsent(${note.uid})",
                                            expectedScope = pending.scope,
                                        ).asUploadResult()
                                } else {
                                    tokenRefresher.withFreshToken(
                                        { token -> cloudContentDataSource.uploadNote(token, uploadReadyNote) },
                                        "uploadNote(${note.uid})",
                                        expectedScope = pending.scope,
                                    )
                                }
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
                            // Persist the confirmed repair base before note metadata, so restart cannot replay an older base.
                            syncMetadataService.advancePendingVersionAfterUpload(EntityType.NOTE, pending, upload.serverVersion)
                            syncableRepository?.updateSyncMetadata(note, upload.serverVersion, upload.syncedAt)
                            retryCoordinator.markUploadSettled(EntityType.NOTE, pending, upload.syncedAt, upload.serverVersion)
                            Napier.d("Uploaded content")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            val serverCopy: suspend () -> Result<ContentState> = {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudContentDataSource.contentState(token, noteId) },
                                    "contentState($noteId)",
                                    expectedScope = pending.scope,
                                )
                            }
                            val resolved =
                                collisions.settle(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    localContentVersion = note.lastUpdated.toEpochMilliseconds(),
                                    error = error,
                                    serverCopy = serverCopy,
                                    currentContentVersion = {
                                        journalNotesRepository
                                            .getNoteById(
                                                noteId,
                                            )?.lastUpdated
                                            ?.toEpochMilliseconds()
                                    },
                                    onDeleted = {
                                        if (syncableRepository != null) {
                                            syncableRepository.deleteFromSync(noteId)
                                        } else {
                                            journalNotesRepository.removeById(noteId)
                                        }
                                        mediaSyncRefStore.deleteScoped(noteId, pending.scope)
                                    },
                                )
                            if (resolved) continue

                            if ((error as? CloudApiException)?.statusCode == 409) {
                                errors.add(
                                    retryCoordinator.handleUploadConflict(
                                        entityType = EntityType.NOTE,
                                        pending = pending,
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

    suspend fun enqueueEverythingOnFirstSync() =
        FirstSyncEnqueuer(
            journalRepository,
            journalNotesRepository,
            syncMetadataService,
            firstSyncEnqueueStore,
            draftUploader::enqueueForRichSync,
        ).enqueue()

    /** Queues pre-existing drafts once per signed-in owner/server, preserving pending deletions. */
    suspend fun enqueueDraftsForRichSync() = draftUploader.enqueueForRichSync()
}
