package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GetTimelineUseCaseTest {

    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var mockGetTimelineDayUseCase: MockGetTimelineDayUseCase
    private lateinit var useCase: GetTimelineUseCase

    @BeforeTest
    fun setUp() {
        mockNotesRepository = MockJournalNotesRepository()
        mockGetTimelineDayUseCase = MockGetTimelineDayUseCase()
        useCase = GetTimelineUseCase(
            notesRepository = mockNotesRepository,
            getTimelineDayUseCase = mockGetTimelineDayUseCase
        )
    }

    @Test
    fun `invoke should return timeline with chronological order`() = runTest {
        // Given
        val note1 = createTestNote("First note", Instant.fromEpochMilliseconds(1000))
        val note2 = createTestNote("Second note", Instant.fromEpochMilliseconds(2000))
        val note3 = createTestNote("Third note", Instant.fromEpochMilliseconds(3000))
        
        mockNotesRepository.allNotes = listOf(note3, note1, note2) // Unordered
        
        // When
        val result = useCase(TimelineSortOrder.CHRONOLOGICAL).first()
        
        // Then
        assertEquals(3, result.days.size)
        assertEquals(3, mockGetTimelineDayUseCase.invocationCalls.size)
        
        // Days should be sorted chronologically
        assertTrue(result.days[0].date <= result.days[1].date)
        assertTrue(result.days[1].date <= result.days[2].date)
    }

    @Test
    fun `invoke should return timeline with reverse chronological order`() = runTest {
        // Given
        val note1 = createTestNote("First note", Instant.fromEpochMilliseconds(1000))
        val note2 = createTestNote("Second note", Instant.fromEpochMilliseconds(2000))
        val note3 = createTestNote("Third note", Instant.fromEpochMilliseconds(3000))
        
        mockNotesRepository.allNotes = listOf(note1, note2, note3)
        
        // When
        val result = useCase(TimelineSortOrder.REVERSE_CHRONOLOGICAL).first()
        
        // Then
        assertEquals(3, result.days.size)
        
        // Days should be sorted in reverse chronological order
        assertTrue(result.days[0].date >= result.days[1].date)
        assertTrue(result.days[1].date >= result.days[2].date)
    }

    @Test
    fun `invoke should group notes by day correctly`() = runTest {
        // Given
        val today = LocalDate(2024, 1, 15)
        val yesterday = LocalDate(2024, 1, 14)
        
        val noteToday1 = createTestNote("Today note 1", today.atStartOfDay())
        val noteToday2 = createTestNote("Today note 2", today.atStartOfDay())
        val noteYesterday = createTestNote("Yesterday note", yesterday.atStartOfDay())
        
        mockNotesRepository.allNotes = listOf(noteToday1, noteYesterday, noteToday2)
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(2, result.days.size) // Two different days
        assertEquals(2, mockGetTimelineDayUseCase.invocationCalls.size)
        
        // Verify that notes are grouped correctly by day
        val todayCall = mockGetTimelineDayUseCase.invocationCalls.find { it.first == today }
        val yesterdayCall = mockGetTimelineDayUseCase.invocationCalls.find { it.first == yesterday }
        
        assertEquals(2, todayCall?.second?.size) // Two notes for today
        assertEquals(1, yesterdayCall?.second?.size) // One note for yesterday
    }

    @Test
    fun `invoke should handle empty notes list`() = runTest {
        // Given
        mockNotesRepository.allNotes = emptyList()
        
        // When
        val result = useCase().first()
        
        // Then
        assertTrue(result.days.isEmpty())
        assertTrue(mockGetTimelineDayUseCase.invocationCalls.isEmpty())
    }

    @Test
    fun `invoke should handle single note`() = runTest {
        // Given
        val singleNote = createTestNote("Single note", Clock.System.now())
        mockNotesRepository.allNotes = listOf(singleNote)
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(1, result.days.size)
        assertEquals(1, mockGetTimelineDayUseCase.invocationCalls.size)
        
        val invocation = mockGetTimelineDayUseCase.invocationCalls.first()
        assertEquals(1, invocation.second.size)
        assertEquals(singleNote, invocation.second.first())
    }

    @Test
    fun `invoke should use default reverse chronological order`() = runTest {
        // Given
        val note1 = createTestNote("Note 1", Instant.fromEpochMilliseconds(1000))
        val note2 = createTestNote("Note 2", Instant.fromEpochMilliseconds(2000))
        mockNotesRepository.allNotes = listOf(note1, note2)
        
        // When
        val result = useCase().first() // No sort order specified
        
        // Then
        assertEquals(2, result.days.size)
        // Should default to reverse chronological order
        assertTrue(result.days[0].date >= result.days[1].date)
    }

    private fun createTestNote(content: String, timestamp: Instant) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = timestamp,
        lastUpdated = timestamp
    )

    private fun LocalDate.atStartOfDay() = this.atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault())

    private class MockJournalNotesRepository : JournalNotesRepository {
        var allNotes = emptyList<JournalNote>()

        override val allNotesObserved: Flow<List<JournalNote>> = flowOf()
            get() = flowOf(allNotes)

        override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())
        override fun observeNotesInRange(start: Instant, end: Instant) = flowOf(emptyList<JournalNote>())
        override fun observeNotesPage(pageSize: Int, offset: Int) = flowOf(emptyList<JournalNote>())
        override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())
        override fun observeRecentNotes(limit: Int) = flowOf(emptyList<JournalNote>())
        override suspend fun create(note: JournalNote): Uuid = note.uid
        override suspend fun remove(note: JournalNote) = Unit
        override suspend fun removeById(noteId: Uuid) = Unit
        override suspend fun create(note: JournalNote, journalId: Uuid) = Unit
        override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) = Unit
    }

    private class MockGetTimelineDayUseCase : GetTimelineDayUseCase {
        val invocationCalls = mutableListOf<Pair<LocalDate, List<JournalNote>>>()

        // Dummy constructor to satisfy inheritance requirements
        constructor() : super(
            summarizeJournalEntriesUseCase = object : SummarizeJournalEntriesUseCase {
                constructor() : super(
                    summarizer = object : app.logdate.client.intelligence.EntrySummarizer {
                        override suspend fun summarize(summaryKey: String, prompt: String): String? = "Test summary"
                    },
                    networkAvailabilityMonitor = object : app.logdate.client.networking.NetworkAvailabilityMonitor {
                        override fun isNetworkAvailable(): Boolean = true
                    }
                )
                override suspend fun invoke(entries: List<JournalNote>) = SummarizeJournalEntriesResult.Success("Test summary")
            },
            getMediaUrisUseCase = object : GetMediaUrisUseCase {
                constructor() : super(
                    mediaManager = object : app.logdate.client.media.MediaManager {
                        override suspend fun getMedia(uri: String) = throw NotImplementedError()
                        override suspend fun exists(mediaId: String) = false
                        override suspend fun getRecentMedia() = flowOf(emptyList<app.logdate.client.media.MediaObject>())
                        override suspend fun queryMediaByDate(start: Instant, end: Instant) = flowOf(emptyList<app.logdate.client.media.MediaObject>())
                        override suspend fun addToDefaultCollection(uri: String) = Unit
                    }
                )
                override fun invoke(day: LocalDate) = flowOf(emptyList<String>())
            },
            extractPeopleUseCase = object : app.logdate.client.domain.entities.ExtractPeopleUseCase {
                constructor() : super(
                    peopleExtractor = object : app.logdate.client.intelligence.entity.people.PeopleExtractor {
                        override suspend fun extractPeople(documentId: String, text: String) = emptyList<app.logdate.shared.model.Person>()
                    }
                )
                override suspend fun invoke(documentId: String, text: String) = emptyList<app.logdate.shared.model.Person>()
            }
        )

        override suspend fun invoke(date: LocalDate, entries: List<JournalNote>): TimelineDay {
            invocationCalls.add(Pair(date, entries))
            return TimelineDay(
                start = entries.minOfOrNull { it.creationTimestamp } ?: Clock.System.now(),
                end = entries.maxOfOrNull { it.creationTimestamp } ?: Clock.System.now(),
                tldr = "Test summary for ${entries.size} entries",
                date = date,
                people = emptyList(),
                events = emptyList(),
                placesVisited = emptyList(),
                parts = emptyList()
            )
        }
    }
}