package app.logdate.client.domain.timeline

import app.logdate.client.domain.entities.ExtractPeopleUseCase
import app.logdate.client.intelligence.AIError
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.EntrySummarizer
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheEntry
import app.logdate.client.intelligence.cache.GenerativeAICacheRequest
import app.logdate.client.intelligence.entity.moments.MomentExtractor
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.MediaPayload
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GetTimelineUseCaseTest {
    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var useCase: GetTimelineUseCase

    @BeforeTest
    fun setUp() {
        mockNotesRepository = MockJournalNotesRepository()
        val getTimelineDayUseCase = buildTimelineDayUseCase()
        useCase =
            GetTimelineUseCase(
                notesRepository = mockNotesRepository,
                getTimelineDayUseCase = getTimelineDayUseCase,
                groupNotesByDayBoundsUseCase = calendarDateGrouper(),
                eventRepository = StubEventRepository(),
            )
    }

    @Test
    fun `invoke should return timeline with chronological order`() =
        runTest {
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
    fun `invoke should return timeline with reverse chronological order`() =
        runTest {
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
    fun `invoke should group notes by day correctly`() =
        runTest {
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
                "Today start should match earliest entry",
            )
            assertEquals(
                todayEntries.maxOf { it.creationTimestamp },
                todayDay.end,
                "Today end should match latest entry",
            )

            val yesterdayDay = daysByDate.getValue(yesterday)
            assertEquals(
                yesterdayEntries.minOf { it.creationTimestamp },
                yesterdayDay.start,
                "Yesterday start should match earliest entry",
            )
            assertEquals(
                yesterdayEntries.maxOf { it.creationTimestamp },
                yesterdayDay.end,
                "Yesterday end should match latest entry",
            )
        }

    @Test
    fun `invoke should handle empty notes list`() =
        runTest {
            // Given
            mockNotesRepository.allNotes = emptyList()

            // When
            val result = useCase().first()

            // Then
            assertTrue(result.days.isEmpty())
        }

    @Test
    fun `invoke should handle single note`() =
        runTest {
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
    fun `invoke should use default reverse chronological order`() =
        runTest {
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

    @Test
    fun `invoke should include distinct semantic places visited for the day`() =
        runTest {
            val timestamp = Instant.parse("2025-01-15T10:00:00Z")
            val homeId = Uuid.random()
            mockNotesRepository.allNotes =
                listOf(
                    createLocatedNote(
                        content = "Breakfast at home",
                        timestamp = timestamp,
                        location =
                            NoteLocation(
                                coordinates = NoteCoordinates(latitude = 37.3317, longitude = -122.0301),
                                place =
                                    NotePlace(
                                        id = homeId,
                                        name = "Home",
                                        latitude = 37.3317,
                                        longitude = -122.0301,
                                    ),
                            ),
                    ),
                    createLocatedNote(
                        content = "Back home again",
                        timestamp = timestamp.plus(1.hours),
                        location =
                            NoteLocation(
                                coordinates = NoteCoordinates(latitude = 37.3317, longitude = -122.0301),
                                place =
                                    NotePlace(
                                        id = homeId,
                                        name = "Home",
                                        latitude = 37.3317,
                                        longitude = -122.0301,
                                    ),
                            ),
                    ),
                )

            val result = useCase().first()

            assertEquals(1, result.days.size)
            assertEquals(
                1,
                result.days
                    .first()
                    .placesVisited
                    .size,
            )
            assertEquals(
                "Home",
                result.days
                    .first()
                    .placesVisited
                    .first()
                    .name,
            )
        }

    @Test
    fun `invoke should include entries and derived day parts for each day`() =
        runTest {
            val timestamp = Instant.parse("2025-01-15T10:00:00Z")
            mockNotesRepository.allNotes =
                listOf(
                    JournalNote.Image(
                        uid = Uuid.random(),
                        mediaRef = "file://photo.jpg",
                        creationTimestamp = timestamp,
                        lastUpdated = timestamp,
                    ),
                    JournalNote.Text(
                        uid = Uuid.random(),
                        content = "Wrote down a few thoughts after lunch.",
                        creationTimestamp = timestamp.plus(2.hours),
                        lastUpdated = timestamp.plus(2.hours),
                    ),
                )

            val result = useCase().first()

            assertEquals(1, result.days.size)
            val day = result.days.first()
            assertEquals(2, day.entries.size)
            assertTrue(day.parts.isNotEmpty())
            assertEquals("file://photo.jpg", day.parts.first().featuredGraphicUri)
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

    private fun createLocatedNote(
        content: String,
        timestamp: Instant,
        location: NoteLocation,
    ) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = timestamp,
        lastUpdated = timestamp,
        location = location,
    )

    private fun LocalDate.atStartOfDay() = this.atStartOfDayIn(TimeZone.currentSystemDefault())

    private fun buildTimelineDayUseCase(): GetTimelineDayUseCase {
        val cache = FakeGenerativeAICache()
        val chatClient = FakeGenerativeAIChatClient(response = "Test summary")
        val networkMonitor = AlwaysOnlineNetworkMonitor()
        val summarizer =
            EntrySummarizer(
                generativeAICache = cache,
                genAIClient = chatClient,
                networkAvailabilityMonitor = networkMonitor,
                dataUsagePolicy =
                    object : DataUsagePolicy {
                        override val policy = MutableStateFlow(DataUsageMode.Unrestricted)

                        override suspend fun currentMode() = DataUsageMode.Unrestricted
                    },
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
                        dataUsagePolicy =
                            object : DataUsagePolicy {
                                override val policy = MutableStateFlow(DataUsageMode.Unrestricted)

                                override suspend fun currentMode() = DataUsageMode.Unrestricted
                            },
                    ),
            )
        val inferMomentsUseCase =
            InferMomentsUseCase(
                momentExtractor =
                    MomentExtractor(
                        generativeAICache = cache,
                        generativeAIChatClient = FakeGenerativeAIChatClient(response = null),
                        networkAvailabilityMonitor = networkMonitor,
                        dataUsagePolicy =
                            object : DataUsagePolicy {
                                override val policy = MutableStateFlow(DataUsageMode.Unrestricted)

                                override suspend fun currentMode() = DataUsageMode.Unrestricted
                            },
                    ),
                audioTagRepository = FakeAudioTagRepository(),
            )
        return GetTimelineDayUseCase(
            summarizeJournalEntriesUseCase = summarizeUseCase,
            getMediaUrisUseCase = getMediaUrisUseCase,
            extractPeopleUseCase = extractPeopleUseCase,
            inferMomentsUseCase = inferMomentsUseCase,
        )
    }

    private class MockJournalNotesRepository : JournalNotesRepository {
        var allNotes = emptyList<JournalNote>()

        override val allNotesObserved: Flow<List<JournalNote>>
            get() = flowOf(allNotes)

        override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())

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

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
    }

    /**
     * Empty [EventRepository] used by the timeline tests — these tests don't care about
     * events, only about how notes are grouped into days.
     */
    private class StubEventRepository : EventRepository {
        override fun observeAllEvents() = flowOf(emptyList<Event>())

        override fun observeEvent(eventId: Uuid) = flowOf<Event?>(null)

        override fun observeEventsForDateRange(
            start: Instant,
            end: Instant,
        ) = flowOf(emptyList<Event>())

        override suspend fun getEventById(eventId: Uuid): Event? = null

        override suspend fun findByExternalCalendarId(externalId: String): Event? = null

        override suspend fun createEvent(event: Event) = Result.success(Unit)

        override suspend fun updateEvent(event: Event) = Result.success(Unit)

        override suspend fun deleteEvent(eventId: Uuid) = Result.success(Unit)

        override fun observeEventsForNote(noteId: Uuid) = flowOf(emptyList<Event>())

        override fun observeNotesForEvent(eventId: Uuid) = flowOf(emptyList<Uuid>())

        override suspend fun linkNoteToEvent(
            eventId: Uuid,
            noteId: Uuid,
        ) = Result.success(Unit)

        override suspend fun unlinkNoteFromEvent(
            eventId: Uuid,
            noteId: Uuid,
        ) = Result.success(Unit)
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

        override suspend fun saveMediaFromFile(
            sourceFilePath: String,
            fileName: String,
            mimeType: String,
        ): String = "file:///tmp/$fileName"
    }
}
