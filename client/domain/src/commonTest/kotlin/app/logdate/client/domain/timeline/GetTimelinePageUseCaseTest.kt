package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GetTimelinePageUseCaseTest {
    @Test
    fun `invoke returns newest complete days and indicates more history`() =
        runTest {
            val repository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            textNote("Jan 3 late", "2025-01-03T18:00:00Z"),
                            textNote("Jan 3 early", "2025-01-03T15:00:00Z"),
                            textNote("Jan 2 late", "2025-01-02T20:00:00Z"),
                            textNote("Jan 2 early", "2025-01-02T16:00:00Z"),
                            textNote("Jan 1", "2025-01-01T18:00:00Z"),
                        ),
                )

            val result =
                GetTimelinePageUseCase(repository, calendarDateGrouper())(
                    TimelinePageRequest(
                        pageSize = 3,
                    ),
                )

            assertEquals(2, result.days.size)
            assertEquals(listOf(2025, 2025), result.days.map { it.date.year })
            assertEquals(listOf(3, 2), result.days.map { it.date.day })
            assertEquals(2, result.days[0].entries.size)
            assertEquals(2, result.days[1].entries.size)
            assertTrue(result.hasMoreOlderContent)
            assertNotNull(result.oldestLoadedTimestamp)
        }

    @Test
    fun `invoke with cursor loads older days without duplicating boundary day`() =
        runTest {
            val repository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            textNote("Jan 3 late", "2025-01-03T18:00:00Z"),
                            textNote("Jan 3 early", "2025-01-03T15:00:00Z"),
                            textNote("Jan 2 late", "2025-01-02T20:00:00Z"),
                            textNote("Jan 2 early", "2025-01-02T16:00:00Z"),
                            textNote("Jan 1", "2025-01-01T18:00:00Z"),
                        ),
                )
            val useCase = GetTimelinePageUseCase(repository, calendarDateGrouper())

            val firstPage = useCase(TimelinePageRequest(pageSize = 3))
            val secondPage =
                useCase(
                    TimelinePageRequest(
                        beforeExclusive = firstPage.oldestLoadedTimestamp,
                        pageSize = 3,
                    ),
                )

            assertEquals(1, secondPage.days.size)
            assertEquals(
                1,
                secondPage.days
                    .single()
                    .date.day,
            )
            assertFalse(secondPage.hasMoreOlderContent)
        }

    @Test
    fun `invoke reports end of history when cursor is already at the oldest note`() =
        runTest {
            val repository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            textNote("Only day late", "2025-01-03T18:00:00Z"),
                            textNote("Only day early", "2025-01-03T15:00:00Z"),
                        ),
                )
            val useCase = GetTimelinePageUseCase(repository, calendarDateGrouper())

            val firstPage = useCase(TimelinePageRequest(pageSize = 1))
            val secondPage =
                useCase(
                    TimelinePageRequest(
                        beforeExclusive = firstPage.oldestLoadedTimestamp,
                        pageSize = 10,
                    ),
                )

            assertTrue(secondPage.days.isEmpty())
            assertFalse(secondPage.hasMoreOlderContent)
            assertEquals(null, secondPage.oldestLoadedTimestamp)
        }

    private fun textNote(
        content: String,
        createdAt: String,
    ): JournalNote.Text {
        val instant = Instant.parse(createdAt)
        return JournalNote.Text(
            uid = Uuid.random(),
            content = content,
            creationTimestamp = instant,
            lastUpdated = instant,
        )
    }

    private class FakeJournalNotesRepository(
        private val notes: List<JournalNote>,
    ) : JournalNotesRepository {
        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(notes)

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(notes.take(limit))

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = notes.firstOrNull { it.uid == noteId }

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

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
    }
}
