package app.logdate.wear.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [SyncManager] implementation that syncs journal notes to a paired phone
 * via the Wear Data Layer API.
 *
 * Text and metadata are sent as [com.google.android.gms.wearable.DataItem]s.
 * Audio files are streamed via [com.google.android.gms.wearable.ChannelClient].
 *
 * Incoming data from the phone is handled by [WearDataLayerListenerService].
 */
class WearDataLayerSyncManager(
    private val dataLayerClient: WearDataLayerClient,
    private val syncMetadataService: SyncMetadataService,
    private val notesRepository: JournalNotesRepository,
    private val noteDataMapper: NoteDataMapper,
    private val clock: Clock = Clock.System,
) : SyncManager {

    private var lastSyncTime: Instant? = null
    private var isSyncing = false

    override fun sync(startNow: Boolean) {
        // Non-blocking trigger. In production this would schedule via AlarmManager
        // or CoroutineScope. For now, callers use the suspend methods directly.
        Napier.d("Wear sync requested (startNow=$startNow)")
    }

    override suspend fun uploadPendingChanges(): SyncResult {
        if (!dataLayerClient.isPhoneConnected()) {
            return SyncResult(
                success = false,
                errors = listOf(
                    SyncError(
                        type = SyncErrorType.NETWORK_ERROR,
                        message = "Phone not connected",
                        retryable = true,
                    ),
                ),
            )
        }

        isSyncing = true
        try {
            val pendingNotes = syncMetadataService.getPendingUploads(EntityType.NOTE)
            if (pendingNotes.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0).also {
                    lastSyncTime = clock.now()
                }
            }

            var uploaded = 0
            val errors = mutableListOf<SyncError>()

            for (pending in pendingNotes) {
                val outcome = when (pending.operation) {
                    PendingOperation.DELETE -> uploadDelete(pending)
                    PendingOperation.CREATE, PendingOperation.UPDATE -> uploadNote(pending)
                }
                when (outcome) {
                    UploadOutcome.SUCCESS -> {
                        uploaded++
                        syncMetadataService.markAsSynced(
                            entityId = pending.entityId,
                            entityType = EntityType.NOTE,
                            syncedAt = clock.now(),
                            version = 1,
                        )
                    }
                    UploadOutcome.SKIPPED -> {
                        // Note no longer exists locally — clear from outbox
                        syncMetadataService.markAsSynced(
                            entityId = pending.entityId,
                            entityType = EntityType.NOTE,
                            syncedAt = clock.now(),
                            version = 0,
                        )
                    }
                    UploadOutcome.FAILED -> {
                        syncMetadataService.incrementRetryCount(pending.entityId, EntityType.NOTE)
                        errors.add(
                            SyncError(
                                type = SyncErrorType.NETWORK_ERROR,
                                message = "Failed to sync note ${pending.entityId}",
                                retryable = true,
                            ),
                        )
                    }
                }
            }

            lastSyncTime = clock.now()
            return SyncResult(
                success = errors.isEmpty(),
                uploadedItems = uploaded,
                errors = errors,
                lastSyncTime = lastSyncTime,
            )
        } finally {
            isSyncing = false
        }
    }

    override suspend fun downloadRemoteChanges(): SyncResult {
        // Downloads are handled reactively by WearDataLayerListenerService.
        // This method is a no-op on the watch side.
        return SyncResult(success = true)
    }

    override suspend fun syncContent(): SyncResult = uploadPendingChanges()

    override suspend fun syncJournals(): SyncResult {
        // Journal metadata sync not yet implemented for Wear.
        return SyncResult(success = true)
    }

    override suspend fun syncAssociations(): SyncResult {
        // Association sync not yet implemented for Wear.
        return SyncResult(success = true)
    }

    override suspend fun fullSync(): SyncResult {
        val notesResult = uploadPendingChanges()
        return notesResult
    }

    override suspend fun getSyncStatus(): SyncStatus {
        val connected = dataLayerClient.isPhoneConnected()
        val pendingCount = syncMetadataService.getPendingCount()
        val lastSync = syncMetadataService.getLastSyncTime(EntityType.NOTE) ?: lastSyncTime

        return SyncStatus(
            isEnabled = connected,
            lastSyncTime = lastSync,
            pendingUploads = pendingCount,
            isSyncing = isSyncing,
            hasErrors = false,
        )
    }

    private suspend fun uploadNote(pending: PendingUpload): UploadOutcome {
        val noteId = try {
            Uuid.parse(pending.entityId)
        } catch (e: IllegalArgumentException) {
            Napier.w("Invalid note ID in pending upload: ${pending.entityId}", e)
            return UploadOutcome.SKIPPED
        }

        val note = notesRepository.getNoteById(noteId)
        if (note == null) {
            Napier.w("Note $noteId not found in repository, skipping sync")
            return UploadOutcome.SKIPPED
        }

        val dataMap = noteDataMapper.toDataMap(note)
        val path = NoteDataMapper.notePath(noteId)
        val putSuccess = dataLayerClient.putDataItem(path, dataMap)

        if (!putSuccess) {
            Napier.w("Failed to put data item for note $noteId")
            return UploadOutcome.FAILED
        }

        // For audio notes, also send the audio file
        if (note is JournalNote.Audio) {
            val channelPath = "${path}/audio"
            val fileSuccess = dataLayerClient.sendFile(channelPath, note.mediaRef)
            if (!fileSuccess) {
                Napier.w("Failed to send audio file for note $noteId")
                // Metadata was sent but audio failed — still count as partial success
                // The phone can request the file again later
            }
        }

        return UploadOutcome.SUCCESS
    }

    private suspend fun uploadDelete(pending: PendingUpload): UploadOutcome {
        val noteId = try {
            Uuid.parse(pending.entityId)
        } catch (e: IllegalArgumentException) {
            Napier.w("Invalid note ID in pending delete: ${pending.entityId}", e)
            return UploadOutcome.SKIPPED
        }

        val path = NoteDataMapper.noteDeletePath(noteId)
        val deleteData = mapOf(
            NoteDataMapper.KEY_UID to noteId.toString(),
            NoteDataMapper.KEY_NOTE_TYPE to "DELETE",
        )
        return if (dataLayerClient.putDataItem(path, deleteData)) {
            UploadOutcome.SUCCESS
        } else {
            UploadOutcome.FAILED
        }
    }

    private enum class UploadOutcome {
        SUCCESS,
        SKIPPED,
        FAILED,
    }
}
