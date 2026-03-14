package app.logdate.client.domain.timeline

import app.logdate.client.domain.entities.ExtractPeopleUseCase
import app.logdate.client.intelligence.AIError
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.EntrySummarizer
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheEntry
import app.logdate.client.intelligence.cache.GenerativeAICacheRequest
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.MediaPayload
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class GetStreamingTimelineUseCaseTest {
    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var useCase: GetStreamingTimelineUseCase

    @BeforeTest
    fun setUp() {
        mockNotesRepository = MockJournalNotesRepository()
        val getTimelineDayUseCase = buildTimelineDayUseCase()
        useCase =
            GetStreamingTimelineUseCase(
                notesRepository = mockNotesRepository,
                getTimelineDayUseCase = getTimelineDayUseCase,
            )
    }

    @Test
    fun `invoke with RecentTimeline request should return recent notes timeline`() =
        runTest {
            // Given
            val dayMillis = 24 * 60 * 60 * 1000L
            val recentNotes =
                listOf(
                    createTestNote("Recent note 1", Instant.fromEpochMilliseconds(0)),
                    createTestNote("Recent note 2", Instant.fromEpochMilliseconds(dayMillis)),
                    createTestNote("Recent note 3", Instant.fromEpochMilliseconds(dayMillis * 2)),
                )
            mockNotesRepository.recentNotes = recentNotes
            val request = StreamingTimelineRequest.RecentTimeline()

            // When
            val result = useCase(request).first()

            // Then
            assertEquals(3, result.days.size)
            assertEquals(50, mockNotesRepository.lastRecentNotesLimit) // Default limit
        }

    @Test
    fun `invoke with RecentTimeline should respect custom page size`() =
        runTest {
            // Given
            val customPageSize = 10
            val request = StreamingTimelineRequest.RecentTimeline(pageSize = customPageSize)
            mockNotesRepository.recentNotes = emptyList()

            // When
            useCase(request).first()

            // Then
            assertEquals(customPageSize, mockNotesRepository.lastRecentNotesLimit)
        }

    @Test
    fun `invoke with RecentTimeline should apply chronological sorting`() =
        runTest {
            // Given
            val dayMillis = 24 * 60 * 60 * 1000L
            val note1 = createTestNote("Note 1", Instant.fromEpochMilliseconds(0))
            val note2 = createTestNote("Note 2", Instant.fromEpochMilliseconds(dayMillis))
            mockNotesRepository.recentNotes = listOf(note2, note1)
            val request =
                StreamingTimelineRequest.RecentTimeline(
                    sortOrder = TimelineSortOrder.CHRONOLOGICAL,
                )

            // When
            val result = useCase(request).first()

            // Then
            assertEquals(2, result.days.size)
            assertTrue(result.days[0].date <= result.days[1].date)
        }

    @Test
    fun `invoke with TimelineInRange should return notes in range`() =
        runTest {
            // Given
            val dayMillis = 24 * 60 * 60 * 1000L
            val start = Instant.fromEpochMilliseconds(0)
            val end = Instant.fromEpochMilliseconds(dayMillis * 3)
            val notesInRange =
                listOf(
                    createTestNote("Range note 1", Instant.fromEpochMilliseconds(dayMillis)),
                    createTestNote("Range note 2", Instant.fromEpochMilliseconds(dayMillis * 2)),
                )
            mockNotesRepository.notesInRange = notesInRange
            val request = StreamingTimelineRequest.TimelineInRange(start, end)

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
    fun `invoke with TimelineInRange should apply reverse chronological sorting by default`() =
        runTest {
            // Given
            val dayMillis = 24 * 60 * 60 * 1000L
            val start = Instant.fromEpochMilliseconds(0)
            val end = Instant.fromEpochMilliseconds(dayMillis * 3)
            val note1 = createTestNote("Note 1", Instant.fromEpochMilliseconds(0))
            val note2 = createTestNote("Note 2", Instant.fromEpochMilliseconds(dayMillis))
            mockNotesRepository.notesInRange = listOf(note1, note2)
            val request = StreamingTimelineRequest.TimelineInRange(start, end)

            // When
            val result = useCase(request).first()

            // Then
            assertEquals(2, result.days.size)
            assertTrue(result.days[0].date >= result.days[1].date)
        }

    @Test
    fun `invoke should create basic timeline for recent notes without full processing`() =
        runTest {
            // Given
            val dayMillis = 24 * 60 * 60 * 1000L
            val recentNotes =
                listOf(
                    createTestNote("My first journal entry", Instant.fromEpochMilliseconds(0)),
                    createTestNote("Another thought for today", Instant.fromEpochMilliseconds(dayMillis)),
                )
            mockNotesRepository.recentNotes = recentNotes
            val request = StreamingTimelineRequest.RecentTimeline()

            // When
            val result = useCase(request).first()

            // Then
            // For recent timeline, it should create basic timeline without calling GetTimelineDayUseCase
            assertEquals(2, result.days.size)

            // Check that basic timeline has empty summaries (UI handles loading state)
            result.days.forEach { day ->
                assertTrue(day.tldr.isEmpty(), "Summary should be empty for streaming timeline")
                assertTrue(day.people.isEmpty())
                assertTrue(day.events.isEmpty())
                assertTrue(day.placesVisited.isEmpty())
            }
        }

    @Test
    fun `invoke should include notes from multiple years`() =
        runTest {
            // Given - notes spanning 2024 and 2025
            val note2024 =
                createTestNote(
                    "Note from 2024",
                    Instant.parse("2024-06-15T10:00:00Z"),
                )
            val note2025Early =
                createTestNote(
                    "Note from early 2025",
                    Instant.parse("2025-01-10T14:30:00Z"),
                )
            val note2025Late =
                createTestNote(
                    "Note from late 2025",
                    Instant.parse("2025-12-20T09:00:00Z"),
                )
            mockNotesRepository.recentNotes = listOf(note2025Late, note2025Early, note2024)

            // When
            val result = useCase(StreamingTimelineRequest.RecentTimeline()).first()

            // Then - all years should be represented
            assertEquals(3, result.days.size, "Should have 3 days from different dates")
            val years = result.days.map { it.date.year }.toSet()
            assertTrue(years.contains(2024), "Should include 2024")
            assertTrue(years.contains(2025), "Should include 2025")
        }

    @Test
    fun `invoke should group multiple notes on same day`() =
        runTest {
            // Given - 3 notes on same day, 1 note on different day
            val sameDay1 = createTestNote("Morning note", Instant.parse("2025-01-15T08:00:00Z"))
            val sameDay2 = createTestNote("Afternoon note", Instant.parse("2025-01-15T14:00:00Z"))
            val sameDay3 = createTestNote("Evening note", Instant.parse("2025-01-15T20:00:00Z"))
            val differentDay = createTestNote("Next day", Instant.parse("2025-01-16T10:00:00Z"))
            mockNotesRepository.recentNotes = listOf(differentDay, sameDay3, sameDay2, sameDay1)

            // When
            val result = useCase(StreamingTimelineRequest.RecentTimeline()).first()

            // Then - should have 2 days, not 4
            assertEquals(2, result.days.size, "Should group notes into 2 days")
        }

    @Test
    fun `invoke should include semantic places in the recent timeline preview`() =
        runTest {
            val timestamp = Instant.parse("2025-01-15T10:00:00Z")
            mockNotesRepository.recentNotes =
                listOf(
                    JournalNote.Text(
                        uid = Uuid.random(),
                        content = "Lunch at school",
                        creationTimestamp = timestamp,
                        lastUpdated = timestamp,
                        location =
                            NoteLocation(
                                coordinates = NoteCoordinates(latitude = 34.0689, longitude = -118.4452),
                                place =
                                    NotePlace(
                                        id = Uuid.random(),
                                        name = "School",
                                        latitude = 34.0689,
                                        longitude = -118.4452,
                                    ),
                            ),
                    ),
                )

            val result = useCase(StreamingTimelineRequest.RecentTimeline()).first()

            assertEquals(1, result.days.size)
            assertEquals(
                listOf("School"),
                result.days
                    .first()
                    .placesVisited
                    .map { place -> place.name },
            )
        }

    @Test
    fun `invoke should include entries and day parts for recent content first cards`() =
        runTest {
            val timestamp = Instant.parse("2025-01-15T10:00:00Z")
            mockNotesRepository.recentNotes =
                listOf(
                    JournalNote.Image(
                        uid = Uuid.random(),
                        mediaRef = "file://photo.jpg",
                        creationTimestamp = timestamp,
                        lastUpdated = timestamp,
                    ),
                    JournalNote.Text(
                        uid = Uuid.random(),
                        content = "Coffee outside before heading downtown.",
                        creationTimestamp = timestamp.plus(1.hours),
                        lastUpdated = timestamp.plus(1.hours),
                    ),
                )

            val result = useCase(StreamingTimelineRequest.RecentTimeline()).first()

            assertEquals(1, result.days.size)
            val day = result.days.first()
            assertEquals(2, day.entries.size)
            assertTrue(day.parts.isNotEmpty(), "Recent timeline should derive day parts for card assembly")
            assertEquals("file://photo.jpg", day.parts.first().featuredGraphicUri)
        }

    @Test
    fun `invoke should handle mixed note types`() =
        runTest {
            // Given - different note types
            val textNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    content = "Text content",
                    creationTimestamp = Instant.parse("2025-01-15T10:00:00Z"),
                    lastUpdated = Instant.parse("2025-01-15T10:00:00Z"),
                )
            val imageNote =
                JournalNote.Image(
                    uid = Uuid.random(),
                    mediaRef = "file://image.jpg",
                    creationTimestamp = Instant.parse("2025-01-15T11:00:00Z"),
                    lastUpdated = Instant.parse("2025-01-15T11:00:00Z"),
                )
            val audioNote =
                JournalNote.Audio(
                    uid = Uuid.random(),
                    mediaRef = "file://audio.m4a",
                    durationMs = 30000,
                    creationTimestamp = Instant.parse("2025-01-15T12:00:00Z"),
                    lastUpdated = Instant.parse("2025-01-15T12:00:00Z"),
                )
            mockNotesRepository.recentNotes = listOf(audioNote, imageNote, textNote)

            // When
            val result = useCase(StreamingTimelineRequest.RecentTimeline()).first()

            // Then - all notes should be grouped into one day
            assertEquals(1, result.days.size, "All notes same day should be 1 timeline day")
        }

    @Test
    fun `invoke should set correct time bounds for grouped day`() =
        runTest {
            // Given - notes at different times on same day (using same-day milliseconds to avoid timezone issues)
            val baseTime = Instant.parse("2025-01-15T12:00:00Z")
            val earlyNote = createTestNote("Early", baseTime)
            val lateNote = createTestNote("Late", baseTime + 1.hours)
            mockNotesRepository.recentNotes = listOf(lateNote, earlyNote)

            // When
            val result = useCase(StreamingTimelineRequest.RecentTimeline()).first()

            // Then - day bounds should span from earliest to latest note
            assertEquals(1, result.days.size, "Notes within same hour should be on same day")
            val day = result.days.first()
            assertEquals(baseTime, day.start, "Start should be earliest note timestamp")
            assertTrue(day.end > day.start, "End should be after start")
        }

    @Test
    fun `invoke should handle empty notes correctly`() =
        runTest {
            // Given
            mockNotesRepository.recentNotes = emptyList()
            mockNotesRepository.notesInRange = emptyList()

            // When
            val recentResult = useCase(StreamingTimelineRequest.RecentTimeline()).first()
            val rangeResult =
                useCase(
                    StreamingTimelineRequest.TimelineInRange(
                        Instant.fromEpochMilliseconds(1000),
                        Instant.fromEpochMilliseconds(2000),
                    ),
                ).first()

            // Then
            assertTrue(recentResult.days.isEmpty())
            assertTrue(rangeResult.days.isEmpty())
        }

    // ========== LOADING SEQUENCE TESTS ==========
    // These tests verify the critical loading behavior that makes the UI feel instant

    @Test
    fun `streaming timeline for RecentTimeline should produce empty summaries proving no AI was called`() =
        runTest {
            // Given - notes that would have summaries if GetTimelineDayUseCase was called
            mockNotesRepository.recentNotes =
                listOf(
                    createTestNote("This is a detailed note about my day", Instant.parse("2025-01-15T10:00:00Z")),
                    createTestNote("Another note with lots of content", Instant.parse("2025-01-16T10:00:00Z")),
                )

            // When - request recent timeline (the fast path)
            val result = useCase(StreamingTimelineRequest.RecentTimeline()).first()

            // Then - all summaries should be empty, proving GetTimelineDayUseCase was NOT called
            // (GetTimelineDayUseCase would populate tldr with AI-generated summary)
            result.days.forEach { day ->
                assertTrue(
                    day.tldr.isEmpty(),
                    "Day ${day.date} has non-empty summary '${day.tldr}' - " +
                        "this means GetTimelineDayUseCase was called, defeating the purpose of streaming",
                )
                assertTrue(
                    day.people.isEmpty(),
                    "Day ${day.date} has people - GetTimelineDayUseCase was called",
                )
            }
        }

    @Test
    fun `streaming timeline should emit immediately without delay`() =
        runTest {
            // Given
            mockNotesRepository.recentNotes =
                listOf(
                    createTestNote("Note", Instant.parse("2025-01-15T10:00:00Z")),
                )

            // When - collect first emission and measure time
            val mark = TimeSource.Monotonic.markNow()
            useCase(StreamingTimelineRequest.RecentTimeline()).first()
            val elapsed = mark.elapsedNow()

            // Then - should emit within 100ms (no network calls, no AI processing)
            assertTrue(
                elapsed.inWholeMilliseconds < 100,
                "First emission should be immediate (<100ms), but took ${elapsed.inWholeMilliseconds}ms. " +
                    "This suggests blocking on slow operations.",
            )
        }

    @Test
    fun `all timeline days should have empty tldr for loading state`() =
        runTest {
            // Given - multiple notes across multiple days
            mockNotesRepository.recentNotes =
                listOf(
                    createTestNote("Day 1 note", Instant.parse("2025-01-15T10:00:00Z")),
                    createTestNote("Day 2 note", Instant.parse("2025-01-16T10:00:00Z")),
                    createTestNote("Day 3 note", Instant.parse("2025-01-17T10:00:00Z")),
                )

            // When
            val result = useCase(StreamingTimelineRequest.RecentTimeline()).first()

            // Then - EVERY day should have empty tldr so UI shows loading placeholder
            assertEquals(3, result.days.size)
            result.days.forEachIndexed { index, day ->
                assertTrue(
                    day.tldr.isEmpty(),
                    "Day $index (${day.date}) should have empty tldr for loading state, but had: '${day.tldr}'",
                )
                assertTrue(
                    day.people.isEmpty(),
                    "Day $index should have empty people list initially",
                )
                assertTrue(
                    day.events.isEmpty(),
                    "Day $index should have empty events list initially",
                )
            }
        }

    @Test
    fun `streaming timeline should enrich recent days after the first content first emission`() =
        runTest {
            val recentNotes =
                listOf(
                    createTestNote("This is a detailed note about my day", Instant.parse("2025-01-15T10:00:00Z")),
                )
            mockNotesRepository.recentNotes = recentNotes
            mockNotesRepository.allNotes = recentNotes

            val emissions = useCase(StreamingTimelineRequest.RecentTimeline()).take(2).toList()

            assertEquals("", emissions[0].days.first().tldr)
            assertEquals("Test summary", emissions[1].days.first().tldr)
            assertEquals(
                1,
                emissions[1]
                    .days
                    .first()
                    .entries.size,
            )
        }

    @Test
    fun `streaming flow should re-emit when underlying data changes`() =
        runTest {
            // Given - a mutable flow that simulates database updates
            val notesFlow =
                MutableStateFlow(
                    listOf(createTestNote("Initial", Instant.parse("2025-01-15T10:00:00Z"))),
                )
            val reactiveRepository =
                object : JournalNotesRepository {
                    override val allNotesObserved = notesFlow

                    override fun observeRecentNotes(limit: Int) = notesFlow

                    override fun observeNotesInRange(
                        start: Instant,
                        end: Instant,
                    ) = notesFlow

                    override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())

                    override fun observeNotesPage(
                        pageSize: Int,
                        offset: Int,
                    ) = flowOf(emptyList<JournalNote>())

                    override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())

                    override suspend fun create(note: JournalNote) = note.uid

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
                }
            val reactiveUseCase = GetStreamingTimelineUseCase(reactiveRepository, buildTimelineDayUseCase())

            // When - collect emissions
            val emissions = mutableListOf<Timeline>()
            val job =
                launch {
                    reactiveUseCase(StreamingTimelineRequest.RecentTimeline()).collect { emissions.add(it) }
                }

            // Wait for initial content-first and enrichment emissions
            advanceUntilIdle()
            assertTrue(emissions.size >= 1, "Should have at least one initial emission")
            assertEquals(1, emissions.first().days.size)

            // Add a new note
            notesFlow.value = notesFlow.value + createTestNote("New note", Instant.parse("2025-01-16T10:00:00Z"))
            advanceUntilIdle()

            // Then - should have re-emitted with updated data
            assertTrue(emissions.size >= 2, "Should re-emit when data changes")
            assertEquals(2, emissions.last().days.size, "Updated emission should have 2 days")

            job.cancel()
        }

    private fun createTestNote(
        content: String,
        timestamp: Instant,
    ) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = timestamp,
        lastUpdated = timestamp,
    )

    private fun buildTimelineDayUseCase(): GetTimelineDayUseCase {
        val cache = FakeGenerativeAICache()
        val chatClient = FakeGenerativeAIChatClient(response = "Test summary")
        val networkMonitor = AlwaysOnlineNetworkMonitor()
        val summarizer =
            EntrySummarizer(
                generativeAICache = cache,
                genAIClient = chatClient,
                networkAvailabilityMonitor = networkMonitor,
            )
        val summarizeUseCase =
            SummarizeJournalEntriesUseCase(
                summarizer = summarizer,
            )
        val mediaManager = FakeMediaManager()
        val getMediaUrisUseCase = GetMediaUrisUseCase(mediaManager)
        val extractPeopleUseCase =
            ExtractPeopleUseCase(
                peopleExtractor =
                    PeopleExtractor(
                        generativeAICache = cache,
                        generativeAIChatClient = FakeGenerativeAIChatClient(response = null),
                        networkAvailabilityMonitor = networkMonitor,
                    ),
            )
        return GetTimelineDayUseCase(
            summarizeJournalEntriesUseCase = summarizeUseCase,
            getMediaUrisUseCase = getMediaUrisUseCase,
            extractPeopleUseCase = extractPeopleUseCase,
        )
    }

    private class MockJournalNotesRepository : JournalNotesRepository {
        var recentNotes = emptyList<JournalNote>()
        var allNotes = emptyList<JournalNote>()
        var notesInRange = emptyList<JournalNote>()
        var lastRecentNotesLimit: Int = 0
        val observeNotesInRangeCalls = mutableListOf<Pair<Instant, Instant>>()

        override val allNotesObserved: Flow<List<JournalNote>>
            get() = flowOf(allNotes)

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> {
            lastRecentNotesLimit = limit
            return flowOf(recentNotes)
        }

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> {
            observeNotesInRangeCalls.add(Pair(start, end))
            return flowOf(notesInRange)
        }

        override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ) = flowOf(emptyList<JournalNote>())

        override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())

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
    }

    private class FakeGenerativeAICache : GenerativeAICache {
        override suspend fun getEntry(request: GenerativeAICacheRequest): GenerativeAICacheEntry? = null

        override suspend fun putEntry(
            request: GenerativeAICacheRequest,
            content: String,
        ) {}

        override suspend fun purge() {}
    }

    private class FakeGenerativeAIChatClient(
        private val response: String?,
    ) : GenerativeAIChatClient {
        override val providerId: String = "fake"
        override val defaultModel: String? = "fake-model"

        override suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse> =
            if (response == null) {
                AIResult.Error(AIError.InvalidResponse)
            } else {
                AIResult.Success(GenerativeAIResponse(response))
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

        override suspend fun queryMediaByDate(
            start: Instant,
            end: Instant,
        ): Flow<List<MediaObject>> = flowOf(emptyList())

        override suspend fun addToDefaultCollection(uri: String) {}

        override suspend fun readMedia(uri: String): MediaPayload =
            MediaPayload(
                fileName = uri.substringAfterLast('/'),
                mimeType = "application/octet-stream",
                sizeBytes = 0,
                data = ByteArray(0),
            )

        override suspend fun saveMedia(payload: MediaPayload): String = "file://stub/${payload.fileName}"
    }
}
