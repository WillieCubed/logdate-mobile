package app.logdate.wear.sync.v2.window

import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.datalayer.NoteDataMapper
import java.security.MessageDigest

internal fun windowNoteFingerprint(
    note: JournalNote,
    media: StoredMediaReferences = StoredMediaReferences.Unchanged,
): String {
    val content =
        when (note) {
            is JournalNote.Text -> note.copy(syncVersion = 0)
            is JournalNote.Audio -> note.copy(syncVersion = 0, mediaRef = media.storedReference(note.mediaRef))
            is JournalNote.Image -> note.copy(syncVersion = 0, mediaRef = media.storedReference(note.mediaRef))
            is JournalNote.Video -> note.copy(syncVersion = 0, mediaRef = media.storedReference(note.mediaRef))
        }
    val json = NoteDataMapper().toDataMap(content).getValue(NoteDataMapper.KEY_JSON_PAYLOAD)
    return MessageDigest.getInstance("SHA-256").digest(json.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}
