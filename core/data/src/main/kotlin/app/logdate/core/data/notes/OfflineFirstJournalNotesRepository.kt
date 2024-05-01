package app.logdate.core.data.notes

import app.logdate.core.data.JournalRepository
import app.logdate.core.data.notes.util.toEntity
import app.logdate.core.data.notes.util.toModel
import app.logdate.core.database.dao.ImageNoteDao
import app.logdate.core.database.dao.TextNoteDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.Instant
import javax.inject.Inject

class OfflineFirstJournalNotesRepository @Inject constructor(
    private val textNoteDao: TextNoteDao,
    private val imageNoteDao: ImageNoteDao,
    private val journalRepository: JournalRepository,
) : JournalNotesRepository {

    override val allNotesObserved: Flow<List<JournalNote>> =
        textNoteDao.getAllNotes().combine(imageNoteDao.getAllNotes()) { textNotes, imageNotes ->
            textNotes.map { it.toModel() } + imageNotes.map { it.toModel() }
        }

    override fun observeNotesInJournal(journalId: String): Flow<List<JournalNote>> {
        return journalRepository.observeJournalById(journalId)
            .combine(allNotesObserved) { _, notes ->
                notes.filter { it.uid == journalId }
            }
        // TODO: Handle journal not found
    }

    override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> {
        return allNotesObserved.filter {
            it.any { note -> note.creationTimestamp in start..end }
        }
    }

    override suspend fun create(note: JournalNote) {
        when (note) {
            is JournalNote.Text -> {
                textNoteDao.addNote(note.toEntity())
            }

            is JournalNote.Image -> {
                imageNoteDao.addNote(note.toEntity())
            }

            is JournalNote.Audio -> TODO()
            is JournalNote.Video -> TODO()
        }
    }

    override suspend fun remove(note: JournalNote) {
        when (note) {
            is JournalNote.Text -> {
                textNoteDao.removeNote(note.uid.toInt())
            }

            is JournalNote.Image -> {
                imageNoteDao.removeNote(note.uid.toInt())
            }

            is JournalNote.Audio -> TODO()
            is JournalNote.Video -> TODO()
        }
    }

    override suspend fun removeById(noteId: String) {
        // TODO: Properly handle deletes, probably need a metadata table just to store the type of note.
        textNoteDao.removeNote(noteId.toInt())
    }

    override suspend fun create(note: String, journalId: String) {

    }

    override suspend fun removeFromJournal(noteId: String, journalId: String) {
        TODO("Not yet implemented")
    }
}