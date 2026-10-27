package app.logdate.wear.sync

import app.logdate.client.sync.datalayer.NoteAckPayload
import kotlin.uuid.Uuid

/** Receives the phone's confirmation that a note, and its audio file, are stored on the phone. */
fun interface WearNoteAckHandler {
    /** [ack] names the version the phone stored; it is null from phones that predate versioned acknowledgements. */
    suspend fun onNoteAcknowledged(
        noteId: Uuid,
        ack: NoteAckPayload?,
    )

    suspend fun onNoteAcknowledged(
        noteId: Uuid,
        ack: NoteAckPayload?,
        transferId: String?,
    ) = onNoteAcknowledged(noteId, ack)
}
