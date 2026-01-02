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
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GetTimelineUseCaseTest {

    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var useCase: GetTimelineUseCase

    @BeforeTest
    fun setUp() {
        mockNotesRepository = MockJournalNotesRepository()
        val getTimelineDayUseCase = buildTimelineDayUseCase()
        useCase = GetTimelineUseCase(
            notesRepository = mockNotesRepository,
            getTimelineDayUseCase = getTimelineDayUseCase
        )
    }

    @Test
    fun `invoke should return timeline with chronological order`() = runTest {
        // Given
        val dayMillis = 24 * 60 * 60 * 1000L
        val note1 = createTestNote("First note", Instant.fromEpochMilliseconds(0))
        val note2 = createTestNote("Second note", Instant.fromEpochMilliseconds(dayMillis))
        val note3 = createTestNote("Third note", Instant.fromEpochMilliseconds(dayMillis * 2))
        
        mockNotesRepository.allNotes = listOf(note3, note1, note2) // Unordered
        
        // When
        val result = useCase(TimelineSortOrder.CHRONOLOGICAL).first()
        
        // Then
        assertEquals(3, result.days.size)
        
        // Days should be sorted chronologically
        assertTrue(result.days[0].date <= result.days[1].date)
        assertTrue(result.days[1].date <= result.days[2].date)
    }

    @Test
    fun `invoke should return timeline with reverse chronological order`() = runTest {
        // Given
        val dayMillis = 24 * 60 * 60 * 1000L
        val note1 = createTestNote("First note", Instant.fromEpochMilliseconds(0))
        val note2 = createTestNote("Second note", Instant.fromEpochMilliseconds(dayMillis))
        val note3 = createTestNote("Third note", Instant.fromEpochMilliseconds(dayMillis * 2))
        
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

        val daysByDate = result.days.associateBy { it.date }
        val todayEntries = listOf(noteToday1, noteToday2)
        val yesterdayEntries = listOf(noteYesterday)

        val todayDay = daysByDate.getValue(today)
        assertEquals(
            todayEntries.minOf { it.creationTimestamp },
            todayDay.start,
            "Today start should match earliest entry"
        )
        assertEquals(
            todayEntries.maxOf { it.creationTimestamp },
            todayDay.end,
            "Today end should match latest entry"
        )

        val yesterdayDay = daysByDate.getValue(yesterday)
        assertEquals(
            yesterdayEntries.minOf { it.creationTimestamp },
            yesterdayDay.start,
            "Yesterday start should match earliest entry"
        )
        assertEquals(
            yesterdayEntries.maxOf { it.creationTimestamp },
            yesterdayDay.end,
            "Yesterday end should match latest entry"
        )
    }

    @Test
    fun `invoke should handle empty notes list`() = runTest {
        // Given
        mockNotesRepository.allNotes = emptyList()
        
        // When
        val result = useCase().first()
        
        // Then
        assertTrue(result.days.isEmpty())
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
        assertEquals(singleNote.creationTimestamp, result.days.first().start)
    }

    @Test
    fun `invoke should use default reverse chronological order`() = runTest {
        // Given
        val dayMillis = 24 * 60 * 60 * 1000L
        val note1 = createTestNote("Note 1", Instant.fromEpochMilliseconds(0))
        val note2 = createTestNote("Note 2", Instant.fromEpochMilliseconds(dayMillis))
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

    private fun LocalDate.atStartOfDay() = this.atStartOfDayIn(TimeZone.currentSystemDefault())

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
        var allNotes = emptyList<JournalNote>()

        override val allNotesObserved: Flow<List<JournalNote>>
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
