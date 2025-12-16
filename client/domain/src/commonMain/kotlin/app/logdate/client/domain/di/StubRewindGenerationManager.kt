package app.logdate.client.domain.di

import app.logdate.client.repository.rewind.RewindGenerationManager
import app.logdate.shared.model.RewindGenerationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Stub implementation of [RewindGenerationManager] for testing and development.
 * 
 * This implementation provides minimal functionality for rewind generation management
 * without relying on database or other external services. It's primarily used for
 * dependency injection when the real implementation is not available or needed.
 */
class StubRewindGenerationManager : RewindGenerationManager {
    // Map to store active requests
    private val activeRequests = mutableMapOf<Uuid, RewindGenerationRequest>()
    
    override suspend fun requestGeneration(startTime: Instant, endTime: Instant): RewindGenerationRequest {
        val requestId = Uuid.random()
        val request = RewindGenerationRequest(
            id = requestId,
            startTime = startTime,
            endTime = endTime,
            requestTime = kotlinx.datetime.Clock.System.now(),
            status = RewindGenerationRequest.Status.PENDING
        )
        activeRequests[requestId] = request
        return request
    }

    override suspend fun getGenerationRequest(requestId: Uuid): RewindGenerationRequest? {
        return activeRequests[requestId]
    }

    override fun observeGenerationStatus(requestId: Uuid): Flow<RewindGenerationRequest?> {
        return flowOf(activeRequests[requestId])
    }

    override suspend fun isGenerationInProgress(startTime: Instant, endTime: Instant): Boolean {
        return activeRequests.values.any { 
            it.startTime == startTime && 
            it.endTime == endTime && 
            (it.status == RewindGenerationRequest.Status.PENDING || 
             it.status == RewindGenerationRequest.Status.PROCESSING) 
        }
    }

    override suspend fun cancelGeneration(requestId: Uuid): Boolean {
        val request = activeRequests[requestId] ?: return false
        activeRequests[requestId] = request.copy(status = RewindGenerationRequest.Status.CANCELLED)
        return true
    }
}