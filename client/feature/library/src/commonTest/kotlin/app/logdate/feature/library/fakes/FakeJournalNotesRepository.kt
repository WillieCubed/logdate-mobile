package app.logdate.feature.library.fakes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Fake implementation of [JournalNotesRepository] for testing.
 */
class FakeJournalNotesRepository(
    initialNotes: List<JournalNote> = emptyList(),
) : JournalNotesRepository {
    private val notesFlow = MutableStateFlow(initialNotes)

    override val allNotesObserved: Flow<List<JournalNote>> = notesFlow

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = notesFlow

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> = notesFlow.map { notes -> notes.filter { it.creationTimestamp in start..end } }

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> = notesFlow.map { it.drop(offset).take(pageSize) }

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = notesFlow

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = notesFlow.map { it.take(limit) }

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = notesFlow.value.find { it.uid == noteId }

    override suspend fun create(note: JournalNote): Uuid {
        notesFlow.value = notesFlow.value + note
        return note.uid
    }

    override suspend fun remove(note: JournalNote) {
        notesFlow.value = notesFlow.value.filter { it.uid != note.uid }
    }

    override suspend fun removeById(noteId: Uuid) {
        notesFlow.value = notesFlow.value.filter { it.uid != noteId }
    }

    override suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    ) {
        notesFlow.value = notesFlow.value + note
    }

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) {
        // No-op for tests
    }
}
