package app.logdate.core.data.notes

import app.logdate.core.data.journals.JournalRepository
import app.logdate.core.data.notes.util.toEntity
import app.logdate.core.data.notes.util.toModel
import app.logdate.core.database.dao.ImageNoteDao
import app.logdate.core.database.dao.TextNoteDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

class OfflineFirstJournalNotesRepository @Inject constructor(
    private val textNoteDao: TextNoteDao,
    private val imageNoteDao: ImageNoteDao,
    private val journalRepository: JournalRepository,
) : JournalNotesRepository, ExportableJournalContentRepository {

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
        TODO("Not yet implemented")
    }

    override suspend fun removeFromJournal(noteId: String, journalId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun exportContentToFile(
        destination: String,
        overwrite: Boolean,
        startTimestamp: Instant,
        endTimestamp: Instant,
    ) {
        val textNotes = runBlocking {
            textNoteDao.getAllNotes().map {
                it.filter { note -> note.created in startTimestamp..endTimestamp }
            }.toList().flatten().map { it.toModel() }
        }
        val backup = JournalContentBackup(textNotes)

        val json = Json.encodeToString(backup)

        val file = File(destination)
        if (file.exists() && !overwrite) {
            throw IllegalStateException("File already exists and overwrite is set to false.")
        }

        file.writeText(json)
    }
}

@Serializable
data class JournalContentBackup(
    val notes: List<JournalNote>,
    val generated: Instant = Clock.System.now(),
)