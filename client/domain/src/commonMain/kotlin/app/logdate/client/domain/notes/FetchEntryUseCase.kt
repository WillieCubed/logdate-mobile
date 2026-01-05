package app.logdate.client.domain.notes

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.repository.journals.JournalNote
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Fetches a specific journal entry by its ID for editing in a new window.
 *
 * Since entries can be of different types (text, image, audio, video), this use case
 * queries each note DAO sequentially until it finds the matching entry by ID.
 *
 * Technical debt: Currently accesses DAOs directly because JournalNotesRepository doesn't
 * expose a "fetch by ID" method. Consider creating a dedicated NoteRepository interface.
 */
class FetchEntryUseCase(
    private val textNoteDao: TextNoteDao,
    private val audioNoteDao: AudioNoteDao,
    private val imageNoteDao: ImageNoteDao,
    private val videoNoteDao: VideoNoteDao,
) {
    suspend operator fun invoke(entryId: Uuid): JournalNote? {
        return try {
            // Try to fetch as text note
            val textNote = runCatching {
                textNoteDao.getNoteOneOff(entryId)
            }.getOrNull()?.let { entity ->
                JournalNote.Text(
                    uid = entity.uid,
                    creationTimestamp = entity.created,
                    lastUpdated = entity.lastUpdated,
                    content = entity.content
                )
            }

            if (textNote != null) {
                Napier.d("FetchEntryUseCase: Found entry $entryId as text note")
                return textNote
            }

            // Try to fetch as image note
            val imageNote = runCatching {
                imageNoteDao.getNoteOneOff(entryId)
            }.getOrNull()?.let { entity ->
                JournalNote.Image(
                    uid = entity.uid,
                    creationTimestamp = entity.created,
                    lastUpdated = entity.lastUpdated,
                    mediaRef = entity.contentUri
                )
            }

            if (imageNote != null) {
                Napier.d("FetchEntryUseCase: Found entry $entryId as image note")
                return imageNote
            }

            // Try to fetch as audio note
            val audioNote = runCatching {
                audioNoteDao.getNoteOneOff(entryId)
            }.getOrNull()?.let { entity ->
                JournalNote.Audio(
                    uid = entity.uid,
                    creationTimestamp = entity.created,
                    lastUpdated = entity.lastUpdated,
                    mediaRef = entity.contentUri
                )
            }

            if (audioNote != null) {
                Napier.d("FetchEntryUseCase: Found entry $entryId as audio note")
                return audioNote
            }

            // Try to fetch as video note
            val videoNote = runCatching {
                videoNoteDao.getNoteOneOff(entryId)
            }.getOrNull()?.let { entity ->
                JournalNote.Video(
                    uid = entity.uid,
                    creationTimestamp = entity.created,
                    lastUpdated = entity.lastUpdated,
                    mediaRef = entity.contentUri
                )
            }

            if (videoNote != null) {
                Napier.d("FetchEntryUseCase: Found entry $entryId as video note")
                return videoNote
            }

            Napier.w("FetchEntryUseCase: Entry not found: $entryId")
            null
        } catch (e: Exception) {
            Napier.e("FetchEntryUseCase: Failed to fetch entry: $entryId", e)
            null
        }
    }
}
