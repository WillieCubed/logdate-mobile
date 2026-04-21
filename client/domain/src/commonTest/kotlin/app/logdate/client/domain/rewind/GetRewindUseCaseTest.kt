package app.logdate.client.domain.rewind

import app.logdate.client.domain.fakes.FakeNetworkAvailabilityMonitor
import app.logdate.client.domain.media.IndexMediaForPeriodUseCase
import app.logdate.client.domain.notes.FetchNotesForDayUseCase
import app.logdate.client.intelligence.AIError
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.availability.RewindAIAvailability
import app.logdate.client.intelligence.availability.RewindAITier
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheEntry
import app.logdate.client.intelligence.cache.GenerativeAICacheRequest
import app.logdate.client.intelligence.curation.BeatBucketer
import app.logdate.client.intelligence.curation.DiversitySelector
import app.logdate.client.intelligence.curation.NoSignalsExtractor
import app.logdate.client.intelligence.curation.PhotoHardFilter
import app.logdate.client.intelligence.curation.RewindMediaCurator
import app.logdate.client.intelligence.curation.SignificanceScorer
import app.logdate.client.intelligence.entity.people.PeopleExtractor
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import app.logdate.client.intelligence.narrative.RewindSequencer
import app.logdate.client.intelligence.narrative.WeekNarrativeSynthesizer
import app.logdate.client.intelligence.rewind.strategy.FullLLMRewindStrategy
import app.logdate.client.intelligence.rewind.strategy.LocalRewindStrategy
import app.logdate.client.intelligence.rewind.strategy.QuotesOnlyLLMRewindStrategy
import app.logdate.client.intelligence.rewind.strategy.RewindStrategySelector
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.MediaPayload
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Location
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindGenerationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit tests for [GetRewindUseCase].
 *
 * Tests the retrieval and state management of rewinds for arbitrary time
 * periods, including handling of in-progress generations, missing data,
 * and future-dated requests.
 */
class GetRewindUseCaseTest {
    private lateinit var rewindRepository: FakeRewindRepository
    private lateinit var generationManager: FakeRewindGenerationManager
    private lateinit var useCase: GetRewindUseCase

    @BeforeTest
    fun setUp() {
        rewindRepository = FakeRewindRepository()
        generationManager = FakeRewindGenerationManager()
        val journalNotesRepository = FakeJournalNotesRepository()
        val fetchNotesForDay = FetchNotesForDayUseCase(journalNotesRepository)
        val indexedMediaRepository = FakeIndexedMediaRepository()
        val indexMediaForPeriod =
            IndexMediaForPeriodUseCase(
                mediaManager = FakeMediaManager(),
                indexedMediaRepository = indexedMediaRepository,
            )
        val networkMonitor = FakeNetworkAvailabilityMonitor()
        val dataUsagePolicy =
            object : DataUsagePolicy {
                override val policy = MutableStateFlow(DataUsageMode.Unrestricted)

                override suspend fun currentMode() = DataUsageMode.Unrestricted
            }
        val narrativeSynthesizer =
            WeekNarrativeSynthesizer(
                generativeAICache = FakeGenerativeAICache(),
                genAIClient = FakeGenerativeAIChatClient(NARRATIVE_RESPONSE),
                networkAvailabilityMonitor = networkMonitor,
                dataUsagePolicy = dataUsagePolicy,
            )
        val rewindSequencer = RewindSequencer()
        val peopleExtractor =
            PeopleExtractor(
                generativeAICache = FakeGenerativeAICache(),
                generativeAIChatClient = FakeGenerativeAIChatClient(null),
                networkAvailabilityMonitor = networkMonitor,
                dataUsagePolicy = dataUsagePolicy,
            )
        val curator =
            RewindMediaCurator(
                signalExtractor = NoSignalsExtractor(),
                hardFilter = PhotoHardFilter(),
                scorer = SignificanceScorer(),
                bucketer = BeatBucketer(),
                selector = DiversitySelector(),
            )
        val localStrategy = LocalRewindStrategy(curator = curator, sequencer = rewindSequencer)
        val fullStrategy =
            FullLLMRewindStrategy(
                narrativeSynthesizer = narrativeSynthesizer,
                curator = curator,
                sequencer = rewindSequencer,
                localFallback = localStrategy,
            )
        val quotesOnlyStrategy = QuotesOnlyLLMRewindStrategy(localFallback = localStrategy)
        val strategySelector =
            RewindStrategySelector(
                availability =
                    object : RewindAIAvailability {
                        override suspend fun current(): RewindAITier = RewindAITier.FULL
                    },
                fullStrategy = fullStrategy,
                quotesOnlyStrategy = quotesOnlyStrategy,
                localStrategy = localStrategy,
            )
        val generateBasicRewindUseCase =
            GenerateBasicRewindUseCase(
                rewindRepository = rewindRepository,
                generationManager = generationManager,
                fetchNotesForDay = fetchNotesForDay,
                indexedMediaRepository = indexedMediaRepository,
                indexMediaForPeriod = indexMediaForPeriod,
                generateRewindTitle = GenerateRewindTitleUseCase(),
                strategySelector = strategySelector,
                peopleExtractor = peopleExtractor,
                locationHistoryRepository = FakeLocationHistoryRepository(),
            )
        useCase =
            GetRewindUseCase(
                rewindRepository = rewindRepository,
                generationManager = generationManager,
                generateBasicRewindUseCase = generateBasicRewindUseCase,
            )
    }

    @Test
    fun `invoke should return Success when rewind exists`() =
        runTest {
            // Given
            val start = Instant.fromEpochMilliseconds(1000)
            val end = Instant.fromEpochMilliseconds(5000)
            val testRewind = buildRewind()
            val params = RewindParams(start, end)

            rewindRepository.rewindResult = testRewind

            // When
            val result = useCase(params).first()

            // Then
            val success = assertIs<RewindQueryResult.Success>(result)
            assertEquals(testRewind, success.rewind)
            assertEquals(1, rewindRepository.getRewindBetweenCalls.size)

            val call = rewindRepository.getRewindBetweenCalls.first()
            assertEquals(start, call.first)
            assertEquals(end, call.second)
        }

    @Test
    fun `invoke should return Generating when generation is in progress`() =
        runTest {
            // Given
            val params =
                RewindParams(
                    start = Instant.fromEpochMilliseconds(2000),
                    end = Instant.fromEpochMilliseconds(6000),
                )
            generationManager.inProgress = true
            rewindRepository.rewindResult = null

            // When
            val result = useCase(params).first()

            // Then — should emit Generating while waiting for the in-progress generation
            assertEquals(RewindQueryResult.Generating, result)
        }

    @Test
    fun `invoke should return Generating when rewind is missing`() =
        runTest {
            // Given
            val params =
                RewindParams(
                    start = Instant.fromEpochMilliseconds(2000),
                    end = Instant.fromEpochMilliseconds(6000),
                )
            generationManager.inProgress = false
            rewindRepository.rewindResult = null

            // When
            val result = useCase(params).first()

            // Then
            assertEquals(RewindQueryResult.Generating, result)
            assertEquals(1, rewindRepository.getRewindBetweenCalls.size)
        }

    @Test
    fun `invoke should handle different time ranges correctly`() =
        runTest {
            // Given
            val params1 =
                RewindParams(
                    start = Instant.fromEpochMilliseconds(1000),
                    end = Instant.fromEpochMilliseconds(2000),
                )
            val params2 =
                RewindParams(
                    start = Instant.fromEpochMilliseconds(5000),
                    end = Instant.fromEpochMilliseconds(10000),
                )

            val rewind1 = buildRewind()
            rewindRepository.rewindResult = rewind1

            // When
            val result1 = useCase(params1).first()

            rewindRepository.rewindResult = null
            val result2 = useCase(params2).first()

            // Then
            val success1 = assertIs<RewindQueryResult.Success>(result1)
            assertEquals(rewind1, success1.rewind)
            assertEquals(RewindQueryResult.Generating, result2)
            assertEquals(2, rewindRepository.getRewindBetweenCalls.size)
        }

    @Test
    fun `invoke should handle multiple calls with same parameters`() =
        runTest {
            // Given
            val params =
                RewindParams(
                    start = Instant.fromEpochMilliseconds(3000),
                    end = Instant.fromEpochMilliseconds(7000),
                )
            val testRewind = buildRewind()
            rewindRepository.rewindResult = testRewind

            // When
            val result1 = useCase(params).first()
            val result2 = useCase(params).first()

            // Then
            val success1 = assertIs<RewindQueryResult.Success>(result1)
            val success2 = assertIs<RewindQueryResult.Success>(result2)
            assertEquals(testRewind, success1.rewind)
            assertEquals(testRewind, success2.rewind)
            assertEquals(2, rewindRepository.getRewindBetweenCalls.size)
        }

    @Test
    fun `invoke should handle edge case time ranges`() =
        runTest {
            // Given - Start and end are the same
            val sameInstant = Instant.fromEpochMilliseconds(5000)
            val params = RewindParams(start = sameInstant, end = sameInstant)
            rewindRepository.rewindResult = null

            // When
            val result = useCase(params).first()

            // Then
            assertEquals(RewindQueryResult.Generating, result)
            assertEquals(1, rewindRepository.getRewindBetweenCalls.size)

            val call = rewindRepository.getRewindBetweenCalls.first()
            assertEquals(sameInstant, call.first)
            assertEquals(sameInstant, call.second)
        }

    @Test
    fun `invoke should handle future time ranges`() =
        runTest {
            // Given - Time range in the future
            val futureStart = Instant.fromEpochMilliseconds(Long.MAX_VALUE - 1000)
            val futureEnd = Instant.fromEpochMilliseconds(Long.MAX_VALUE)
            val params = RewindParams(start = futureStart, end = futureEnd)
            rewindRepository.rewindResult = null

            // When
            val result = useCase(params).first()

            // Then
            assertEquals(RewindQueryResult.NotReady, result)
        }

    private fun buildRewind(): Rewind {
        val now = Clock.System.now()
        return Rewind(
            uid = Uuid.random(),
            startDate = now,
            endDate = now,
            generationDate = now,
            label = "2024#01",
            title = "Test rewind",
            content = emptyList(),
        )
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

    private class FakeRewindRepository : RewindRepository {
        var rewindResult: Rewind? = null
        val getRewindBetweenCalls = mutableListOf<Pair<Instant, Instant>>()

        override fun getRewindBetween(
            start: Instant,
            end: Instant,
        ): Flow<Rewind?> {
            getRewindBetweenCalls.add(Pair(start, end))
            return flowOf(rewindResult)
        }

        override fun getAllRewinds(): Flow<List<Rewind>> = flowOf(emptyList())

        override fun getRewind(uid: Uuid): Flow<Rewind> = flowOf(buildFallbackRewind())

        override suspend fun isRewindAvailable(
            start: Instant,
            end: Instant,
        ): Boolean = false

        @Deprecated("Use GenerateBasicRewindUseCase instead")
        override suspend fun createRewind(
            start: Instant,
            end: Instant,
        ): Rewind = buildFallbackRewind()

        override suspend fun saveRewind(rewind: Rewind) {}

        override fun getRewindsInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<Rewind>> = flowOf(emptyList())

        override suspend fun deleteRewind(uid: Uuid) {}

        override suspend fun markAsViewed(uid: Uuid) {}

        override suspend fun tagAsMilestone(
            uid: Uuid,
            signal: String,
        ) {}

        private fun buildFallbackRewind(): Rewind {
            val now = Clock.System.now()
            return Rewind(
                uid = Uuid.random(),
                startDate = now,
                endDate = now,
                generationDate = now,
                label = "2024#01",
                title = "Fallback rewind",
                content = emptyList(),
            )
        }
    }

    private companion object {
        private val NARRATIVE_RESPONSE =
            """
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

    private class FakeRewindGenerationManager : RewindGenerationManager {
        var inProgress = false

        override suspend fun requestGeneration(
            startTime: Instant,
            endTime: Instant,
        ): RewindGenerationRequest =
            RewindGenerationRequest(
                id = Uuid.random(),
                startTime = startTime,
                endTime = endTime,
                requestTime = Clock.System.now(),
                status = RewindGenerationRequest.Status.PENDING,
                details = null,
                rewindId = null,
            )

        override suspend fun getGenerationRequest(requestId: Uuid): RewindGenerationRequest? = null

        override fun observeGenerationStatus(requestId: Uuid): Flow<RewindGenerationRequest?> = flowOf(null)

        override suspend fun isGenerationInProgress(
            startTime: Instant,
            endTime: Instant,
        ): Boolean = inProgress

        override suspend fun updateRequestStatus(
            id: Uuid,
            status: RewindGenerationRequest.Status,
            details: String?,
        ): Boolean = true

        override suspend fun cancelGeneration(requestId: Uuid): Boolean = false
    }

    private class FakeJournalNotesRepository : JournalNotesRepository {
        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())

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

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override suspend fun create(note: JournalNote): Uuid = note.uid

        override suspend fun remove(note: JournalNote) {}

        override suspend fun removeById(noteId: Uuid) {}

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) {}

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) {}

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = null

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
    }

    private class FakeIndexedMediaRepository : IndexedMediaRepository {
        override suspend fun indexImage(
            uri: String,
            timestamp: Instant,
        ): IndexedMedia.Image = IndexedMedia.Image(Uuid.random(), uri, timestamp)

        override suspend fun indexVideo(
            uri: String,
            timestamp: Instant,
            duration: Duration,
        ): IndexedMedia.Video = IndexedMedia.Video(Uuid.random(), uri, timestamp, duration = duration)

        override suspend fun getByUid(uid: Uuid): IndexedMedia? = null

        override fun getForPeriod(
            startTime: Instant,
            endTime: Instant,
        ): Flow<List<IndexedMedia>> = flowOf(emptyList())

        override suspend fun isIndexed(uri: String): Boolean = false

        override suspend fun remove(uid: Uuid): Boolean = false

        override suspend fun updateCaption(
            uid: Uuid,
            caption: String?,
        ): IndexedMedia? = null

        override fun observeAllMedia(): Flow<List<IndexedMedia>> = flowOf(emptyList())

        override fun getMediaCount(): Flow<Int> = flowOf(0)

        override suspend fun getExifMetadata(uid: Uuid): app.logdate.client.repository.media.ExifMetadata? = null
    }

    private class FakeMediaManager : MediaManager {
        override suspend fun getMedia(uri: String): MediaObject =
            MediaObject.Image(
                uri = uri,
                size = 0,
                name = "mock",
                timestamp = Clock.System.now(),
            )

        override suspend fun exists(mediaId: String): Boolean = false

        override suspend fun getRecentMedia(limit: Int): Flow<List<MediaObject>> = flowOf(emptyList())

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

    private class FakeLocationHistoryRepository : LocationHistoryRepository {
        override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = emptyList()

        override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = flowOf(emptyList())

        override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = emptyList()

        override suspend fun getLocationHistoryBetween(
            startTime: Instant,
            endTime: Instant,
        ): List<LocationHistoryItem> = emptyList()

        override suspend fun getLastLocation(): LocationHistoryItem? = null

        override fun observeLastLocation(): Flow<LocationHistoryItem?> = flowOf(null)

        override suspend fun logLocation(
            location: Location,
            userId: String,
            deviceId: String,
            confidence: Float,
            isGenuine: Boolean,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun logLocation(record: LocationLogRecord): Result<Unit> = Result.success(Unit)

        override suspend fun deleteLocationEntry(
            userId: String,
            deviceId: String,
            timestamp: Instant,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteLocationsBetween(
            startTime: Instant,
            endTime: Instant,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun getLocationCount(): Int = 0
    }
}
