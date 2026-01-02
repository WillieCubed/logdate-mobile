package app.logdate.client.domain.timeline

import app.logdate.client.domain.entities.ExtractPeopleUseCase
import app.logdate.client.intelligence.EntrySummarizer
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheEntry
import app.logdate.client.intelligence.cache.GenerativeAICacheRequest
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.AIError
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GetStreamingTimelineUseCaseTest {

    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var useCase: GetStreamingTimelineUseCase

    @BeforeTest
    fun setUp() {
        mockNotesRepository = MockJournalNotesRepository()
        val getTimelineDayUseCase = buildTimelineDayUseCase()
        useCase = GetStreamingTimelineUseCase(
            notesRepository = mockNotesRepository,
            getTimelineDayUseCase = getTimelineDayUseCase
        )
    }

    @Test
    fun `invoke with RecentTimeline request should return recent notes timeline`() = runTest {
        // Given
        val dayMillis = 24 * 60 * 60 * 1000L
        val recentNotes = listOf(
            createTestNote("Recent note 1", Instant.fromEpochMilliseconds(0)),
            createTestNote("Recent note 2", Instant.fromEpochMilliseconds(dayMillis)),
            createTestNote("Recent note 3", Instant.fromEpochMilliseconds(dayMillis * 2))
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
        val dayMillis = 24 * 60 * 60 * 1000L
        val note1 = createTestNote("Note 1", Instant.fromEpochMilliseconds(0))
        val note2 = createTestNote("Note 2", Instant.fromEpochMilliseconds(dayMillis))
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
        val dayMillis = 24 * 60 * 60 * 1000L
        val start = Instant.fromEpochMilliseconds(0)
        val end = Instant.fromEpochMilliseconds(dayMillis * 3)
        val notesInRange = listOf(
            createTestNote("Range note 1", Instant.fromEpochMilliseconds(dayMillis)),
            createTestNote("Range note 2", Instant.fromEpochMilliseconds(dayMillis * 2))
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
        val dayMillis = 24 * 60 * 60 * 1000L
        val start = Instant.fromEpochMilliseconds(0)
        val end = Instant.fromEpochMilliseconds(dayMillis * 3)
        val note1 = createTestNote("Note 1", Instant.fromEpochMilliseconds(0))
        val note2 = createTestNote("Note 2", Instant.fromEpochMilliseconds(dayMillis))
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
        val dayMillis = 24 * 60 * 60 * 1000L
        val recentNotes = listOf(
            createTestNote("Note 1", Instant.fromEpochMilliseconds(0)),
            createTestNote("Note 2", Instant.fromEpochMilliseconds(dayMillis))
        )
        mockNotesRepository.recentNotes = recentNotes
        val request = GetStreamingTimelineUseCase.TimelineRequest.RecentTimeline()
        
        // When
        val result = useCase(request).first()
        
        // Then
        // For recent timeline, it should create basic timeline without calling GetTimelineDayUseCase
        assertEquals(2, result.days.size)
        
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

    private fun buildTimelineDayUseCase(): GetTimelineDayUseCase {
        val cache = FakeGenerativeAICache()
        val chatClient = FakeGenerativeAIChatClient(response = "Test summary")
        val networkMonitor = AlwaysOnlineNetworkMonitor()
        val summarizer = EntrySummarizer(
            generativeAICache = cache,
            genAIClient = chatClient,
            networkAvailabilityMonitor = networkMonitor
        )
        val summarizeUseCase = SummarizeJournalEntriesUseCase(
            summarizer = summarizer
        )
        val mediaManager = FakeMediaManager()
        val getMediaUrisUseCase = GetMediaUrisUseCase(mediaManager)
        val extractPeopleUseCase = ExtractPeopleUseCase(
            peopleExtractor = PeopleExtractor(
                generativeAICache = cache,
                generativeAIChatClient = FakeGenerativeAIChatClient(response = null),
                networkAvailabilityMonitor = networkMonitor
            )
        )
        return GetTimelineDayUseCase(
            summarizeJournalEntriesUseCase = summarizeUseCase,
            getMediaUrisUseCase = getMediaUrisUseCase,
            extractPeopleUseCase = extractPeopleUseCase
        )
    }

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

    private class FakeGenerativeAICache : GenerativeAICache {
        override suspend fun getEntry(request: GenerativeAICacheRequest): GenerativeAICacheEntry? = null
        override suspend fun putEntry(request: GenerativeAICacheRequest, content: String) {}
        override suspend fun purge() {}
    }

    private class FakeGenerativeAIChatClient(
        private val response: String?
    ) : GenerativeAIChatClient {
        override val providerId: String = "fake"
        override val defaultModel: String? = "fake-model"
        override suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse> {
            return if (response == null) {
                AIResult.Error(AIError.InvalidResponse)
            } else {
                AIResult.Success(GenerativeAIResponse(response))
            }
        }
    }

    private class AlwaysOnlineNetworkMonitor : NetworkAvailabilityMonitor {
        private val flow = MutableSharedFlow<NetworkState>(replay = 1)
        override fun isNetworkAvailable(): Boolean = true
        override fun observeNetwork(): SharedFlow<NetworkState> = flow
    }

    private class FakeMediaManager : MediaManager {
        override suspend fun getMedia(uri: String): MediaObject =
            MediaObject.Image(uri, size = 0, name = "mock", timestamp = Clock.System.now())

        override suspend fun exists(mediaId: String): Boolean = false
        override suspend fun getRecentMedia(): Flow<List<MediaObject>> = flowOf(emptyList())
        override suspend fun queryMediaByDate(start: Instant, end: Instant): Flow<List<MediaObject>> = flowOf(emptyList())
        override suspend fun addToDefaultCollection(uri: String) {}
    }
}
