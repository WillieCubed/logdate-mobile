package app.logdate.client.data.notes

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import kotlin.uuid.Uuid

/** Updates derived media metadata atomically while preserving the note's identity and history. */
internal class NoteMediaMetadataWriter(
    private val audioNotes: AudioNoteDao,
    private val imageNotes: ImageNoteDao,
    private val videoNotes: VideoNoteDao,
    private val transactions: SyncTransactionManager,
    private val metadata: SyncMetadataService,
    private val mediaReferences: StoredMediaReferences,
    private val transcriptions: TranscriptionRepository?,
    private val findNote: suspend (Uuid) -> JournalNote?,
) {
    suspend fun fillAudioDuration(
        noteId: Uuid,
        mediaRef: String,
        durationMs: Long,
    ): Boolean {
        if (durationMs <= 0L) return false
        return transactions.withTransaction {
            val changed = audioNotes.updateDurationIfUnknown(noteId, mediaReferences.storedReference(mediaRef), durationMs) > 0
            if (changed) metadata.enqueuePending(noteId.toString(), EntityType.NOTE, PendingOperation.UPDATE)
            changed
        }
    }

    /** Retains the existing local URI-rebinding behavior used by sync and media migration. */
    suspend fun updateReference(
        noteId: Uuid,
        mediaRef: String,
    ) {
        val storedRef = mediaReferences.storedReference(mediaRef)
        transactions.withTransaction {
            val previous = (findNote(noteId) as? JournalNote.Audio)?.mediaRef
            if (previous != null) transcriptions?.rebindMediaReference(noteId, previous, storedRef)
            imageNotes.updateContentUri(noteId, storedRef)
            audioNotes.updateContentUri(noteId, storedRef)
            videoNotes.updateContentUri(noteId, storedRef)
        }
    }
}
