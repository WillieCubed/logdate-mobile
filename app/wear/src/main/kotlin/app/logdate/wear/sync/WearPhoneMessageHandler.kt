package app.logdate.wear.sync

import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.datalayer.NoteAckPayload
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Reacts to the phone: a note acknowledgement clears it from the watch's outbox, and a sync
 * request or the phone becoming reachable again sends whatever is still waiting.
 */
class WearPhoneMessageHandler(
    private val ackHandler: WearNoteAckHandler,
    private val syncManager: SyncManager,
) {
    suspend fun onMessage(
        path: String,
        data: ByteArray = ByteArray(0),
    ) {
        when {
            WearAudioRequestPaths.isNoteAckPath(path) -> acknowledge(path, data)
            path == WearAudioRequestPaths.SYNC_REQUEST_PATH -> sendPendingChanges()
            else -> Napier.d("Unknown message path: $path")
        }
    }

    suspend fun onPhoneReachable() = sendPendingChanges()

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
        } catch (e: Exception) {
            Napier.w("Failed to send pending changes to the phone", e)
        }
    }
}
