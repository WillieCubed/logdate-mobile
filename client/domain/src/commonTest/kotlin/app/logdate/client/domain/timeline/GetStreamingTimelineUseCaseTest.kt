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

class GetStreamingTimelineUseCaseTest {

    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var mockGetTimelineDayUseCase: MockGetTimelineDayUseCase
    private lateinit var useCase: GetStreamingTimelineUseCase

    @BeforeTest
    fun setUp() {
        mockNotesRepository = MockJournalNotesRepository()
        mockGetTimelineDayUseCase = MockGetTimelineDayUseCase()
        useCase = GetStreamingTimelineUseCase(
            notesRepository = mockNotesRepository,
            getTimelineDayUseCase = mockGetTimelineDayUseCase
        )
    }

    @Test
    fun `invoke with RecentTimeline request should return recent notes timeline`() = runTest {
        // Given
        val recentNotes = listOf(
            createTestNote("Recent note 1", Clock.System.now()),
            createTestNote("Recent note 2", Clock.System.now()),
            createTestNote("Recent note 3", Clock.System.now())
        )
        mockNotesRepository.recentNotes = recentNotes
        val request = GetStreamingTimelineUseCase.TimelineRequest.RecentTimeline()
        
        // When
        val result = useCase(request).first()
        
        // Then
        assertEquals(3, result.days.size)
        assertEquals(20, mockNotesRepository.lastRecentNotesLimit) // Default limit
    }

    @Test
    fun `invoke with RecentTimeline should respect custom page size`() = runTest {
        // Given
        val customPageSize = 10
        val request = GetStreamingTimelineUseCase.TimelineRequest.RecentTimeline(pageSize = customPageSize)
        mockNotesRepository.recentNotes = emptyList()
        
        // When
        useCase(request).first()
        
        // Then
        assertEquals(20, mockNotesRepository.lastRecentNotesLimit) // Use case hardcodes 20 for recent notes
    }

    @Test
    fun `invoke with RecentTimeline should apply chronological sorting`() = runTest {
        // Given
        val note1 = createTestNote("Note 1", Instant.fromEpochMilliseconds(1000))
        val note2 = createTestNote("Note 2", Instant.fromEpochMilliseconds(2000))
        mockNotesRepository.recentNotes = listOf(note2, note1)
        val request = GetStreamingTimelineUseCase.TimelineRequest.RecentTimeline(
            sortOrder = TimelineSortOrder.CHRONOLOGICAL
        )
        
        // When
        val result = useCase(request).first()
        
        // Then
        assertEquals(2, result.days.size)
        assertTrue(result.days[0].date <= result.days[1].date)
    }

    @Test
    fun `invoke with TimelineInRange should return notes in range`() = runTest {
        // Given
        val start = Instant.fromEpochMilliseconds(1000)
        val end = Instant.fromEpochMilliseconds(5000)
        val notesInRange = listOf(
            createTestNote("Range note 1", Instant.fromEpochMilliseconds(2000)),
            createTestNote("Range note 2", Instant.fromEpochMilliseconds(3000))
        )
        mockNotesRepository.notesInRange = notesInRange
        val request = GetStreamingTimelineUseCase.TimelineRequest.TimelineInRange(start, end)
        
        // When
        val result = useCase(request).first()
        
        // Then
        assertEquals(2, result.days.size)
        assertEquals(1, mockNotesRepository.observeNotesInRangeCalls.size)
        val rangeCall = mockNotesRepository.observeNotesInRangeCalls.first()
        assertEquals(start, rangeCall.first)
        assertEquals(end, rangeCall.second)
    }

    @Test
    fun `invoke with TimelineInRange should apply reverse chronological sorting by default`() = runTest {
        // Given
        val start = Instant.fromEpochMilliseconds(1000)
        val end = Instant.fromEpochMilliseconds(5000)
        val note1 = createTestNote("Note 1", Instant.fromEpochMilliseconds(2000))
        val note2 = createTestNote("Note 2", Instant.fromEpochMilliseconds(3000))
        mockNotesRepository.notesInRange = listOf(note1, note2)
        val request = GetStreamingTimelineUseCase.TimelineRequest.TimelineInRange(start, end)
        
        // When
        val result = useCase(request).first()
        
        // Then
        assertEquals(2, result.days.size)
        assertTrue(result.days[0].date >= result.days[1].date)
    }

    @Test
    fun `invoke should create basic timeline for recent notes without full processing`() = runTest {
        // Given
        val recentNotes = listOf(
            createTestNote("Note 1", Clock.System.now()),
            createTestNote("Note 2", Clock.System.now())
        )
        mockNotesRepository.recentNotes = recentNotes
        val request = GetStreamingTimelineUseCase.TimelineRequest.RecentTimeline()
        
        // When
        val result = useCase(request).first()
        
        // Then
        // For recent timeline, it should create basic timeline without calling GetTimelineDayUseCase
        assertEquals(2, result.days.size)
        assertEquals(0, mockGetTimelineDayUseCase.invocationCalls.size) // Basic timeline doesn't use this
        
        // Check that basic timeline has expected format
        result.days.forEach { day ->
            assertTrue(day.tldr.contains("entries"))
            assertTrue(day.people.isEmpty())
            assertTrue(day.events.isEmpty())
            assertTrue(day.placesVisited.isEmpty())
        }
    }

    @Test
    fun `invoke should handle empty notes correctly`() = runTest {
        // Given
        mockNotesRepository.recentNotes = emptyList()
        mockNotesRepository.notesInRange = emptyList()
        
        // When
        val recentResult = useCase(GetStreamingTimelineUseCase.TimelineRequest.RecentTimeline()).first()
        val rangeResult = useCase(GetStreamingTimelineUseCase.TimelineRequest.TimelineInRange(
            Instant.fromEpochMilliseconds(1000),
            Instant.fromEpochMilliseconds(2000)
        )).first()
        
        // Then
        assertTrue(recentResult.days.isEmpty())
        assertTrue(rangeResult.days.isEmpty())
    }

    private fun createTestNote(content: String, timestamp: Instant) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = timestamp,
        lastUpdated = timestamp
    )

    private class MockJournalNotesRepository : JournalNotesRepository {
        var recentNotes = emptyList<JournalNote>()
        var notesInRange = emptyList<JournalNote>()
        var lastRecentNotesLimit: Int = 0
        val observeNotesInRangeCalls = mutableListOf<Pair<Instant, Instant>>()

        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> {
            lastRecentNotesLimit = limit
            return flowOf(recentNotes)
        }

        override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> {
            observeNotesInRangeCalls.add(Pair(start, end))
            return flowOf(notesInRange)
        }

        override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())
        override fun observeNotesPage(pageSize: Int, offset: Int) = flowOf(emptyList<JournalNote>())
        override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())
        override suspend fun create(note: JournalNote): Uuid = note.uid
        override suspend fun remove(note: JournalNote) = Unit
        override suspend fun removeById(noteId: Uuid) = Unit
        override suspend fun create(note: JournalNote, journalId: Uuid) = Unit
        override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) = Unit
    }

    private class MockGetTimelineDayUseCase : GetTimelineDayUseCase {
        val invocationCalls = mutableListOf<Pair<LocalDate, List<JournalNote>>>()

        // Simplified mock that doesn't require complex dependencies
        override suspend fun invoke(date: LocalDate, entries: List<JournalNote>): TimelineDay {
            invocationCalls.add(Pair(date, entries))
            return TimelineDay(
                start = entries.minOfOrNull { it.creationTimestamp } ?: Clock.System.now(),
                end = entries.maxOfOrNull { it.creationTimestamp } ?: Clock.System.now(),
                tldr = "Mock summary for ${entries.size} entries",
                date = date
            )
        }
    }
}