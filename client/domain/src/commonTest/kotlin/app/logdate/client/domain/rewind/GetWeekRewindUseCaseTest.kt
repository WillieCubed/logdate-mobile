package app.logdate.client.domain.rewind

import app.logdate.client.domain.media.IndexMediaForPeriodUseCase
import app.logdate.client.domain.notes.FetchNotesForDayUseCase
import app.logdate.client.domain.fakes.FakeNetworkAvailabilityMonitor
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheEntry
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.client.intelligence.narrative.RewindSequencer
import app.logdate.client.intelligence.narrative.WeekNarrativeSynthesizer
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindGenerationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.uuid.Uuid

class GetWeekRewindUseCaseTest {

    private lateinit var rewindRepository: FakeRewindRepository
    private lateinit var useCase: GetWeekRewindUseCase

    @BeforeTest
    fun setUp() {
        rewindRepository = FakeRewindRepository()
        val generationManager = FakeRewindGenerationManager()
        val journalNotesRepository = FakeJournalNotesRepository()
        val fetchNotesForDay = FetchNotesForDayUseCase(journalNotesRepository)
        val indexedMediaRepository = FakeIndexedMediaRepository()
        val indexMediaForPeriod = IndexMediaForPeriodUseCase(
            mediaManager = FakeMediaManager(),
            indexedMediaRepository = indexedMediaRepository
        )
        val networkMonitor = FakeNetworkAvailabilityMonitor()
        val narrativeSynthesizer = WeekNarrativeSynthesizer(
            generativeAICache = FakeGenerativeAICache(),
            genAIClient = FakeGenerativeAIChatClient(NARRATIVE_RESPONSE),
            networkAvailabilityMonitor = networkMonitor
        )
        val rewindSequencer = RewindSequencer()
        val peopleExtractor = PeopleExtractor(
            generativeAICache = FakeGenerativeAICache(),
            generativeAIChatClient = FakeGenerativeAIChatClient(null),
            networkAvailabilityMonitor = networkMonitor
        )
        val generateBasicRewindUseCase = GenerateBasicRewindUseCase(
            rewindRepository = rewindRepository,
            generationManager = generationManager,
            fetchNotesForDay = fetchNotesForDay,
            indexedMediaRepository = indexedMediaRepository,
            indexMediaForPeriod = indexMediaForPeriod,
            generateRewindTitle = GenerateRewindTitleUseCase(),
            narrativeSynthesizer = narrativeSynthesizer,
            rewindSequencer = rewindSequencer,
            peopleExtractor = peopleExtractor
        )
        val getRewindUseCase = GetRewindUseCase(
            rewindRepository = rewindRepository,
            generationManager = generationManager,
            generateBasicRewindUseCase = generateBasicRewindUseCase
        )
        useCase = GetWeekRewindUseCase(getRewindUseCase = getRewindUseCase)
    }

    @Test
    fun `invoke should request rewind for current week starting Sunday by default`() = runTest {
        // Given
        rewindRepository.rewindForPeriod = buildRewind()

        // When
        useCase().first()

        // Then
        assertEquals(1, rewindRepository.invocations.size)
        val params = rewindRepository.invocations.first()

        assertTrue(params.start <= params.end)

        val timeDifference = params.end.toEpochMilliseconds() - params.start.toEpochMilliseconds()
        val expectedWeekMs = 7 * 24 * 60 * 60 * 1000L
        assertTrue(timeDifference <= expectedWeekMs)
    }

    @Test
    fun `invoke should handle custom week start day`() = runTest {
        // Given
        rewindRepository.rewindForPeriod = buildRewind()

        // When
        useCase(weekStart = DayOfWeek.MONDAY).first()

        // Then
        assertEquals(1, rewindRepository.invocations.size)
        val params = rewindRepository.invocations.first()

        assertTrue(params.start <= params.end)
    }

    @Test
    fun `invoke should handle different week start days`() = runTest {
        // Given
        rewindRepository.rewindForPeriod = buildRewind()

        // When
        val result1 = useCase(weekStart = DayOfWeek.SUNDAY).first()
        val result2 = useCase(weekStart = DayOfWeek.MONDAY).first()
        val result3 = useCase(weekStart = DayOfWeek.FRIDAY).first()

        // Then
        assertEquals(3, rewindRepository.invocations.size)
        assertTrue(result1 is RewindQueryResult.Success)
        assertTrue(result2 is RewindQueryResult.Success)
        assertTrue(result3 is RewindQueryResult.Success)

        rewindRepository.invocations.forEach { params ->
            assertTrue(params.start <= params.end)
        }
    }

    @Test
    fun `invoke should return Success when rewind is available`() = runTest {
        // Given
        val expectedRewind = buildRewind()
        rewindRepository.rewindForPeriod = expectedRewind

        // When
        val result = useCase().first()

        // Then
        assertEquals(RewindQueryResult.Success(expectedRewind), result)
        assertEquals(1, rewindRepository.invocations.size)
    }

    @Test
    fun `invoke should delegate to GetRewindUseCase correctly`() = runTest {
        // Given
        rewindRepository.rewindForPeriod = buildRewind()

        // When
        useCase(weekStart = DayOfWeek.WEDNESDAY).first()

        // Then
        assertEquals(1, rewindRepository.invocations.size)
        val params = rewindRepository.invocations.first()
        val timezone = TimeZone.currentSystemDefault()

        val startLocalDate = params.start.toLocalDateTime(timezone).date
        val startOfStartDay = startLocalDate.atStartOfDayIn(timezone)
        assertEquals(startOfStartDay, params.start)

        val endLocalDate = params.end.toLocalDateTime(timezone).date
        val startOfEndDay = endLocalDate.atStartOfDayIn(timezone)
        assertEquals(startOfEndDay, params.end)
    }

    @Test
    fun `invoke should handle all days of week as start day`() = runTest {
        // Given
        rewindRepository.rewindForPeriod = buildRewind()

        // When
        val daysOfWeek = DayOfWeek.entries
        daysOfWeek.forEach { dayOfWeek ->
            useCase(weekStart = dayOfWeek).first()
        }

        // Then
        assertEquals(daysOfWeek.size, rewindRepository.invocations.size)

        rewindRepository.invocations.forEach { params ->
            assertTrue(params.start <= params.end)
        }
    }

    private fun buildRewind(
        start: Instant = Clock.System.now(),
        end: Instant = Clock.System.now()
    ): Rewind {
        return Rewind(
            uid = Uuid.random(),
            startDate = start,
            endDate = end,
            generationDate = Clock.System.now(),
            label = "2024#01",
            title = "Test rewind",
            content = emptyList()
        )
    }

    private class FakeGenerativeAICache : GenerativeAICache {
        override suspend fun getEntry(key: String): GenerativeAICacheEntry? = null
        override suspend fun putEntry(key: String, value: String) {}
        override suspend fun purge() {}
    }

    private class FakeGenerativeAIChatClient(
        private val response: String?
    ) : GenerativeAIChatClient {
        override suspend fun submit(prompts: List<GenerativeAIChatMessage>): String? = response
    }

    private class FakeRewindRepository : RewindRepository {
        val invocations = mutableListOf<RewindParams>()
        var rewindForPeriod: Rewind? = null

        override fun getAllRewinds(): Flow<List<Rewind>> = flowOf(emptyList())

        override fun getRewind(uid: Uuid): Flow<Rewind> = flowOf(
            rewindForPeriod ?: buildFallbackRewind(uid)
        )

        override fun getRewindBetween(start: Instant, end: Instant): Flow<Rewind?> {
            invocations.add(RewindParams(start, end))
            return flowOf(rewindForPeriod)
        }

        override suspend fun isRewindAvailable(start: Instant, end: Instant): Boolean {
            return rewindForPeriod != null
        }

        @Deprecated("Use GenerateBasicRewindUseCase instead")
        override suspend fun createRewind(start: Instant, end: Instant): Rewind {
            return buildFallbackRewind(Uuid.random()).copy(startDate = start, endDate = end)
        }

        override suspend fun saveRewind(rewind: Rewind) {
            rewindForPeriod = rewind
        }

        private fun buildFallbackRewind(uid: Uuid): Rewind {
            val now = Clock.System.now()
            return Rewind(
                uid = uid,
                startDate = now,
                endDate = now,
                generationDate = now,
                label = "2024#01",
                title = "Fallback rewind",
                content = emptyList()
            )
        }
    }

    private class FakeRewindGenerationManager : RewindGenerationManager {
        override suspend fun requestGeneration(startTime: Instant, endTime: Instant): RewindGenerationRequest {
            return RewindGenerationRequest(
                id = Uuid.random(),
                startTime = startTime,
                endTime = endTime,
                requestTime = Clock.System.now(),
                status = RewindGenerationRequest.Status.PENDING,
                details = null,
                rewindId = null
            )
        }

        override suspend fun getGenerationRequest(requestId: Uuid): RewindGenerationRequest? = null

        override fun observeGenerationStatus(requestId: Uuid): Flow<RewindGenerationRequest?> = flowOf(null)

        override suspend fun isGenerationInProgress(startTime: Instant, endTime: Instant): Boolean = false

        override suspend fun cancelGeneration(requestId: Uuid): Boolean = false
    }

    private class FakeJournalNotesRepository : JournalNotesRepository {
        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override suspend fun create(note: JournalNote): Uuid = note.uid

        override suspend fun remove(note: JournalNote) {}

        override suspend fun removeById(noteId: Uuid) {}

        override suspend fun create(note: JournalNote, journalId: Uuid) {}

        override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {}
    }

    private class FakeIndexedMediaRepository : IndexedMediaRepository {
        override suspend fun indexImage(uri: String, timestamp: Instant): IndexedMedia.Image {
            return IndexedMedia.Image(Uuid.random(), uri, timestamp)
        }

        override suspend fun indexVideo(uri: String, timestamp: Instant, duration: Duration): IndexedMedia.Video {
            return IndexedMedia.Video(Uuid.random(), uri, timestamp, duration = duration)
        }

        override suspend fun getByUid(uid: Uuid): IndexedMedia? = null

        override fun getForPeriod(startTime: Instant, endTime: Instant): Flow<List<IndexedMedia>> = flowOf(emptyList())

        override suspend fun isIndexed(uri: String): Boolean = false

        override suspend fun remove(uid: Uuid): Boolean = false

        override suspend fun updateCaption(uid: Uuid, caption: String?): IndexedMedia? = null
    }

    private class FakeMediaManager : MediaManager {
        override suspend fun getMedia(uri: String): MediaObject = MediaObject.Image(
            uri = uri,
            size = 0,
            name = "mock",
            timestamp = Clock.System.now()
        )

        override suspend fun exists(mediaId: String): Boolean = false

        override suspend fun getRecentMedia(): Flow<List<MediaObject>> = flowOf(emptyList())

        override suspend fun queryMediaByDate(start: Instant, end: Instant): Flow<List<MediaObject>> = flowOf(emptyList())

        override suspend fun addToDefaultCollection(uri: String) {}
    }

    private companion object {
        private val NARRATIVE_RESPONSE = """
            {
              "themes": ["test"],
              "emotionalTone": "calm",
              "storyBeats": [
                {
                  "moment": "Test moment",
                  "context": "Test context",
                  "emotionalWeight": "neutral",
                  "evidenceIds": []
                }
              ],
              "overallNarrative": "Test narrative."
            }
        """.trimIndent()
    }
}
