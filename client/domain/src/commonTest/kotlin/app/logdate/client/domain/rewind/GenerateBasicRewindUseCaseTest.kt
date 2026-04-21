package app.logdate.client.domain.rewind

import app.logdate.client.domain.media.IndexMediaForPeriodUseCase
import app.logdate.client.domain.notes.FetchNotesForDayUseCase
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
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.RewindGenerationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit tests for [GenerateBasicRewindUseCase].
 *
 * Verifies the logic for generating a "Rewind" (narrative summary) for a
 * specific time period, including content filtering, error handling for
 * empty periods, and integration with various data sources like notes,
 * media, and location history.
 */
class GenerateBasicRewindUseCaseTest {
    @Test
    fun `returns NoContent when media flow never emits and notes are empty`() =
        runTest {
            val rewindRepository = RecordingRewindRepository()
            val generationManager = FakeRewindGenerationManager()
            val notesRepository = FakeJournalNotesRepository()
            val mediaRepository = ConfigurableIndexedMediaRepository(mediaFlow = emptyFlow())
            val useCase =
                createUseCase(
                    rewindRepository = rewindRepository,
                    generationManager = generationManager,
                    notesRepository = notesRepository,
                    indexedMediaRepository = mediaRepository,
                )
            val start = Instant.fromEpochMilliseconds(1_000)
            val end = Instant.fromEpochMilliseconds(2_000)

            val result = useCase(start, end)

            assertEquals(GenerateBasicRewindResult.NoContent, result)
            assertNull(rewindRepository.savedRewind)
            assertEquals(RewindGenerationRequest.Status.FAILED, generationManager.lastUpdatedStatus)
            assertEquals("No content available for rewind", generationManager.lastUpdatedDetails)
        }

    @Test
    fun `excludes notes on the exclusive end day`() =
        runTest {
            val timezone = TimeZone.currentSystemDefault()
            val start = LocalDate(2026, 4, 1).atStartOfDayIn(timezone)
            val end = LocalDate(2026, 4, 2).atStartOfDayIn(timezone)
            val includedNote =
                JournalNote.Text(
                    creationTimestamp = start,
                    lastUpdated = start,
                    content = "Included note",
                )
            val excludedNote =
                JournalNote.Text(
                    creationTimestamp = end,
                    lastUpdated = end,
                    content = "Excluded note",
                )
            val rewindRepository = RecordingRewindRepository()
            val useCase =
                createUseCase(
                    rewindRepository = rewindRepository,
                    notesRepository = FakeJournalNotesRepository(listOf(includedNote, excludedNote)),
                    indexedMediaRepository = ConfigurableIndexedMediaRepository(flowOf(emptyList())),
                )

            val result = useCase(start, end)

            val success = assertIs<GenerateBasicRewindResult.Success>(result)
            // The strategy adds structural panels (e.g. a PersonalityCard opener) around
            // text evidence; the contract under test is that the exclusive-end note is
            // never rendered, so assert on the TextNote panels specifically.
            val textPanels = success.rewind.content.filterIsInstance<RewindContent.TextNote>()
            assertEquals(1, textPanels.size)
            assertEquals("Included note", textPanels.single().content)
            assertNotNull(rewindRepository.savedRewind)
        }

    private fun createUseCase(
        rewindRepository: RecordingRewindRepository = RecordingRewindRepository(),
        generationManager: FakeRewindGenerationManager = FakeRewindGenerationManager(),
        notesRepository: FakeJournalNotesRepository = FakeJournalNotesRepository(),
        indexedMediaRepository: ConfigurableIndexedMediaRepository = ConfigurableIndexedMediaRepository(flowOf(emptyList())),
    ): GenerateBasicRewindUseCase {
        val fetchNotesForDay = FetchNotesForDayUseCase(notesRepository)
        val indexMediaForPeriod =
            IndexMediaForPeriodUseCase(
                mediaManager = FakeMediaManager(),
                indexedMediaRepository = indexedMediaRepository,
            )
        val networkMonitor = OfflineNetworkAvailabilityMonitor()
        val dataUsagePolicy =
            object : DataUsagePolicy {
                override val policy: Flow<DataUsageMode> = flowOf(DataUsageMode.Restricted)

                override suspend fun currentMode(): DataUsageMode = DataUsageMode.Restricted
            }
        val narrativeSynthesizer =
            WeekNarrativeSynthesizer(
                generativeAICache = FakeGenerativeAICache(),
                genAIClient = NoOpGenerativeAIChatClient(),
                networkAvailabilityMonitor = networkMonitor,
                dataUsagePolicy = dataUsagePolicy,
            )
        val peopleExtractor =
            PeopleExtractor(
                generativeAICache = FakeGenerativeAICache(),
                generativeAIChatClient = NoOpGenerativeAIChatClient(),
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
        val sequencer = RewindSequencer()
        val localStrategy = LocalRewindStrategy(curator = curator, sequencer = sequencer)
        val fullStrategy =
            FullLLMRewindStrategy(
                narrativeSynthesizer = narrativeSynthesizer,
                curator = curator,
                sequencer = sequencer,
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
        return GenerateBasicRewindUseCase(
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
    }
}

private class RecordingRewindRepository : RewindRepository {
    var savedRewind: Rewind? = null

    override fun getAllRewinds(): Flow<List<Rewind>> = flowOf(emptyList())

    override fun getRewind(uid: Uuid): Flow<Rewind> = flowOf(savedRewind ?: error("No rewind saved"))

    override fun getRewindBetween(
        start: Instant,
        end: Instant,
    ): Flow<Rewind?> = flowOf(null)

    override suspend fun isRewindAvailable(
        start: Instant,
        end: Instant,
    ): Boolean = false

    @Deprecated("Use GenerateBasicRewindUseCase instead")
    override suspend fun createRewind(
        start: Instant,
        end: Instant,
    ): Rewind = error("Unsupported in test")

    override suspend fun saveRewind(rewind: Rewind) {
        savedRewind = rewind
    }

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
}

private class FakeRewindGenerationManager : RewindGenerationManager {
    var lastUpdatedStatus: RewindGenerationRequest.Status? = null
    var lastUpdatedDetails: String? = null

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
        )

    override suspend fun getGenerationRequest(requestId: Uuid): RewindGenerationRequest? = null

    override fun observeGenerationStatus(requestId: Uuid): Flow<RewindGenerationRequest?> = flowOf(null)

    override suspend fun isGenerationInProgress(
        startTime: Instant,
        endTime: Instant,
    ): Boolean = false

    override suspend fun updateRequestStatus(
        id: Uuid,
        status: RewindGenerationRequest.Status,
        details: String?,
    ): Boolean {
        lastUpdatedStatus = status
        lastUpdatedDetails = details
        return true
    }

    override suspend fun cancelGeneration(requestId: Uuid): Boolean = false
}

private class FakeJournalNotesRepository(
    private val notes: List<JournalNote> = emptyList(),
) : JournalNotesRepository {
    override val allNotesObserved: Flow<List<JournalNote>> = flowOf(notes)

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> =
        flowOf(
            notes.filter { note ->
                note.creationTimestamp >= start && note.creationTimestamp < end
            },
        )

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = null

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

    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
}

private class ConfigurableIndexedMediaRepository(
    private val mediaFlow: Flow<List<IndexedMedia>>,
) : IndexedMediaRepository {
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
    ): Flow<List<IndexedMedia>> = mediaFlow

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
            name = "test",
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
            fileName = "test",
            mimeType = "application/octet-stream",
            sizeBytes = 0,
            data = ByteArray(0),
        )

    override suspend fun saveMedia(payload: MediaPayload): String = "file://test/${payload.fileName}"

    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String = "file://test/$fileName"
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

    override suspend fun logLocation(record: LocationLogRecord): Result<Unit> = Result.success(Unit)

    override suspend fun logLocation(
        location: app.logdate.shared.model.Location,
        userId: String,
        deviceId: String,
        confidence: Float,
        isGenuine: Boolean,
    ): Result<Unit> = Result.success(Unit)

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

private class FakeGenerativeAICache : GenerativeAICache {
    override suspend fun getEntry(request: GenerativeAICacheRequest): GenerativeAICacheEntry? = null

    override suspend fun putEntry(
        request: GenerativeAICacheRequest,
        content: String,
    ) {}

    override suspend fun purge() {}
}

private class NoOpGenerativeAIChatClient : GenerativeAIChatClient {
    override val providerId: String = "test"
    override val defaultModel: String? = "test-model"

    override suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse> =
        AIResult.Error(app.logdate.client.intelligence.AIError.InvalidResponse)
}

private class OfflineNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
    private val state = MutableSharedFlow<NetworkState>(replay = 1)

    init {
        state.tryEmit(NetworkState.NotConnected(lastConnected = Instant.DISTANT_PAST))
    }

    override fun isNetworkAvailable(): Boolean = false

    override fun observeNetwork(): MutableSharedFlow<NetworkState> = state
}
