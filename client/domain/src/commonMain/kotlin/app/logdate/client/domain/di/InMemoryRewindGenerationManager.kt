package app.logdate.client.domain.di

import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.shared.model.RewindGenerationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * In-memory [RewindGenerationManager] for local-only targets and tests.
 *
 * It tracks generation requests in process without relying on external services.
 */
class InMemoryRewindGenerationManager : RewindGenerationManager {
    // Map to store active requests
    private val activeRequests = mutableMapOf<Uuid, RewindGenerationRequest>()

    override suspend fun requestGeneration(
        startTime: Instant,
        endTime: Instant,
    ): RewindGenerationRequest {
        val requestId = Uuid.random()
        val request =
            RewindGenerationRequest(
                id = requestId,
                startTime = startTime,
                endTime = endTime,
                requestTime = Clock.System.now(),
                status = RewindGenerationRequest.Status.PENDING,
            )
        activeRequests[requestId] = request
        return request
    }

    override suspend fun getGenerationRequest(requestId: Uuid): RewindGenerationRequest? = activeRequests[requestId]

    override fun observeGenerationStatus(requestId: Uuid): Flow<RewindGenerationRequest?> = flowOf(activeRequests[requestId])

    override suspend fun isGenerationInProgress(
        startTime: Instant,
        endTime: Instant,
    ): Boolean =
        activeRequests.values.any {
            it.startTime == startTime &&
                it.endTime == endTime &&
                (
                    it.status == RewindGenerationRequest.Status.PENDING ||
                        it.status == RewindGenerationRequest.Status.PROCESSING
                )
        }

    override suspend fun updateRequestStatus(
        id: Uuid,
        status: RewindGenerationRequest.Status,
        details: String?,
    ): Boolean {
        val request = activeRequests[id] ?: return false
        activeRequests[id] = request.copy(status = status, details = details)
        return true
    }

    override suspend fun cancelGeneration(requestId: Uuid): Boolean {
        val request = activeRequests[requestId] ?: return false
        activeRequests[requestId] = request.copy(status = RewindGenerationRequest.Status.CANCELLED)
        return true
    }
}
