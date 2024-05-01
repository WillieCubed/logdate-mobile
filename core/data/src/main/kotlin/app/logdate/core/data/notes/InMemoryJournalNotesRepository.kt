package app.logdate.core.data.notes

import app.logdate.core.data.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject

class InMemoryJournalNotesRepository @Inject constructor(
    private val journalRepository: JournalRepository,
) : JournalNotesRepository {

    private val notes = TEST_NOTES.toMutableList()

    override val allNotesObserved: Flow<List<JournalNote>> = MutableStateFlow(notes)

    override fun observeNotesInJournal(journalId: String): Flow<List<JournalNote>> {
        TODO("Not yet implemented")
    }

    override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> {
        TODO("Not yet implemented")
    }

    override suspend fun create(note: JournalNote) {
        notes.add(note)
    }

    override suspend fun create(note: String, journalId: String) {
        // TODO: Verify journal exists
        TODO("Not yet implemented")
    }

    override suspend fun remove(note: JournalNote) {
        notes.remove(note)
    }

    override suspend fun removeById(noteId: String) {
        notes.removeIf { it.uid == noteId }
    }

    override suspend fun removeFromJournal(noteId: String, journalId: String) {
        // TODO: Verify journal exists
        TODO("Not yet implemented")
    }
}

val TEST_NOTES: List<JournalNote> = listOf(
    JournalNote.Text(
        content = "This is a test note",
        uid = "note-1",
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now(),
    ),
    JournalNote.Text(
        content = "This is another test note",
        uid = "note-11",
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now(),
    ),
    JournalNote.Image(
        mediaRef = "media-1",
        uid = "note-2",
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now(),
    ),
    JournalNote.Video(
        mediaRef = "media-2",
        uid = "note-3",
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now(),
    )
)
