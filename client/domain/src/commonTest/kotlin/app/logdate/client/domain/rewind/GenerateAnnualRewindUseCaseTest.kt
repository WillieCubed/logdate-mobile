package app.logdate.client.domain.rewind

import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheEntry
import app.logdate.client.intelligence.cache.GenerativeAICacheRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import app.logdate.client.intelligence.narrative.AnnualRewindSequencer
import app.logdate.client.intelligence.narrative.YearNarrativeSynthesizer
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindGenerationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit tests for [GenerateAnnualRewindUseCase].
 *
 * Ensures that the annual rewind generation correctly aggregates data for
 * a full year and gracefully handles scenarios where insufficient data
 * (such as missing weekly rewinds) is available.
 */
class GenerateAnnualRewindUseCaseTest {
    @Test
    fun `returns NoContent when annual source flows do not emit`() =
        runTest {
            val rewindRepository = EmptyFlowAnnualRewindRepository()
            val generationManager = RecordingAnnualGenerationManager()
            val useCase =
                GenerateAnnualRewindUseCase(
                    rewindRepository = rewindRepository,
                    generationManager = generationManager,
                    yearNarrativeSynthesizer =
                        YearNarrativeSynthesizer(
                            generativeAICache = FakeAnnualGenerativeAICache(),
                            genAIClient = NoOpAnnualGenerativeAIChatClient(),
                            networkAvailabilityMonitor = OfflineAnnualNetworkAvailabilityMonitor(),
                            dataUsagePolicy =
                                object : DataUsagePolicy {
                                    override val policy: Flow<DataUsageMode> = flowOf(DataUsageMode.Restricted)

                                    override suspend fun currentMode(): DataUsageMode = DataUsageMode.Restricted
                                },
                        ),
                    annualRewindSequencer = AnnualRewindSequencer(),
                )

            val result = useCase(2025)

            assertEquals(GenerateBasicRewindResult.NoContent, result)
            assertEquals(RewindGenerationRequest.Status.FAILED, generationManager.lastUpdatedStatus)
            assertEquals("Not enough weekly rewinds", generationManager.lastUpdatedDetails)
        }
}

private class EmptyFlowAnnualRewindRepository : RewindRepository {
    override fun getAllRewinds(): Flow<List<Rewind>> = flowOf(emptyList())

    override fun getRewind(uid: Uuid): Flow<Rewind> = flowOf(dummyRewind())

    override fun getRewindBetween(
        start: Instant,
        end: Instant,
    ): Flow<Rewind?> = emptyFlow()

    override suspend fun isRewindAvailable(
        start: Instant,
        end: Instant,
    ): Boolean = false

    @Deprecated("Use GenerateBasicRewindUseCase instead")
    override suspend fun createRewind(
        start: Instant,
        end: Instant,
    ): Rewind = error("Unsupported in test")

    override suspend fun saveRewind(rewind: Rewind) {}

    override fun getRewindsInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<Rewind>> = emptyFlow()

    override suspend fun deleteRewind(uid: Uuid) {}

    override suspend fun markAsViewed(uid: Uuid) {}

    override suspend fun tagAsMilestone(
        uid: Uuid,
        signal: String,
    ) {}

    private fun dummyRewind(): Rewind {
        val now = Clock.System.now()
        return Rewind(
            uid = Uuid.random(),
            startDate = now,
            endDate = now,
            generationDate = now,
            label = "test",
            title = "test",
            content = emptyList(),
        )
    }
}

private class RecordingAnnualGenerationManager : RewindGenerationManager {
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

private class FakeAnnualGenerativeAICache : GenerativeAICache {
    override suspend fun getEntry(request: GenerativeAICacheRequest): GenerativeAICacheEntry? = null

    override suspend fun putEntry(
        request: GenerativeAICacheRequest,
        content: String,
    ) {}

    override suspend fun purge() {}
}

private class NoOpAnnualGenerativeAIChatClient : GenerativeAIChatClient {
    override val providerId: String = "test"
    override val defaultModel: String? = "test-model"

    override suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse> =
        AIResult.Error(app.logdate.client.intelligence.AIError.InvalidResponse)
}

private class OfflineAnnualNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
    private val state = MutableSharedFlow<NetworkState>(replay = 1)

    init {
        state.tryEmit(NetworkState.NotConnected(lastConnected = Instant.DISTANT_PAST))
    }

    override fun isNetworkAvailable(): Boolean = false

    override fun observeNetwork(): MutableSharedFlow<NetworkState> = state
}
