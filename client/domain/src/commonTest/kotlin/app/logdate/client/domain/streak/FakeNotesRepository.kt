package app.logdate.client.domain.streak

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeNotesRepository(
    private val notesPerDay: Map<LocalDate, List<JournalNote>> = emptyMap(),
) : JournalNotesRepository {
    override val allNotesObserved: Flow<List<JournalNote>> =
        flowOf(notesPerDay.values.flatten())

    override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())

    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ) = flowOf(emptyList<JournalNote>())

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ) = flowOf(emptyList<JournalNote>())

    override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())

    override fun observeRecentNotes(limit: Int) = flowOf(emptyList<JournalNote>())

    override suspend fun create(note: JournalNote): Uuid = note.uid

    override suspend fun remove(note: JournalNote) = Unit

    override suspend fun removeById(noteId: Uuid) = Unit

    override suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    ) = Unit

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) = Unit

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = null

    override suspend fun getNotesForDay(day: LocalDate): List<JournalNote> = notesPerDay[day] ?: emptyList()

    override suspend fun getDatesWithEntries(
        start: LocalDate,
        end: LocalDate,
    ): Set<LocalDate> = notesPerDay.keys.filter { it in start..end }.toSet()
}
