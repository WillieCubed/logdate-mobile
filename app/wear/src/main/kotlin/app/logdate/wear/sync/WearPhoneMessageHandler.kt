package app.logdate.wear.sync

import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.datalayer.NoteAckPayload
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.client.sync.datalayer.v2.WearPaths
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/**
 * Reacts to the phone: a note acknowledgement clears it from the watch's outbox, and a sync
 * request or the phone becoming reachable again sends whatever is still waiting.
 */
class WearPhoneMessageHandler(
    private val ackHandler: WearNoteAckHandler,
    private val syncManager: SyncManager,
    private val receiptsPushed: suspend (String?, ByteArray) -> Int = { _, _ -> 0 },
    private val legacyAcksAllowed: suspend () -> Boolean = { true },
    private val phoneReturned: suspend () -> Unit = {},
) {
    suspend fun onMessage(
        path: String,
        data: ByteArray = ByteArray(0),
        sourceNodeId: String? = null,
    ) {
        when {
            path == WearPaths.RECEIPTS_PUSH -> applyReceipts(sourceNodeId, data)
            WearAudioRequestPaths.isNoteAckPath(path) -> if (legacyAcksAllowed()) acknowledge(path, data)
            path == WearAudioRequestPaths.SYNC_REQUEST_PATH -> sendPendingChanges()
            else -> Napier.d("Unknown message path: $path")
        }
    }

    suspend fun onPhoneReachable() = sendPendingChanges()

    private suspend fun applyReceipts(
        sourceNodeId: String?,
        data: ByteArray,
    ) {
        try {
            if (receiptsPushed(sourceNodeId, data) > 0) sendPendingChanges()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not apply the phone's delivery receipts", e)
        }
    }

    private suspend fun acknowledge(
        path: String,
        data: ByteArray,
    ) {
        val noteId =
            try {
                WearAudioRequestPaths.noteIdFromAckPath(path)
            } catch (e: IllegalArgumentException) {
                Napier.w("Invalid note ID in ack path: $path", e)
                return
            }
        try {
            ackHandler.onNoteAcknowledged(noteId, readAck(noteId, data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Failed to record the phone's acknowledgement of note $noteId", e)
        }
    }

    /**
     * The phone sends an acknowledgement only once it has stored the note, so a payload this build
     * cannot read is handled like the empty one older phones send, and logged as the error it is.
     */
    private fun readAck(
        noteId: Uuid,
        data: ByteArray,
    ): NoteAckPayload? =
        try {
            NoteAckPayload.decode(data)
        } catch (e: IllegalArgumentException) {
            Napier.e("Unreadable acknowledgement payload for note $noteId; treating it as unversioned", e)
            null
        }

    private suspend fun sendPendingChanges() {
        try {
            syncManager.fullSync()
            phoneReturned()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Failed to send pending changes to the phone", e)
        }
    }
}
