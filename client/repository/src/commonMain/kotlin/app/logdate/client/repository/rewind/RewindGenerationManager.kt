package app.logdate.client.repository.rewind

import app.logdate.shared.model.RewindGenerationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Manager for handling rewind generation operations.
 */
interface RewindGenerationManager {
    /**
     * Requests generation of a rewind for the specified time period.
     * 
     * @param startTime The start of the time period
     * @param endTime The end of the time period
     * @return The created generation request
     */
    suspend fun requestGeneration(startTime: Instant, endTime: Instant): RewindGenerationRequest
    
    /**
     * Gets a specific generation request by ID.
     * 
     * @param requestId The ID of the request to retrieve
     * @return The generation request if found, null otherwise
     */
    suspend fun getGenerationRequest(requestId: Uuid): RewindGenerationRequest?
    
    /**
     * Gets the status of a specific generation request.
     * 
     * @param requestId The ID of the request to retrieve
     * @return Flow emitting the request status updates
     */
    fun observeGenerationStatus(requestId: Uuid): Flow<RewindGenerationRequest?>
    
    /**
     * Checks if there's an active generation in progress for the specified time period.
     * 
     * @param startTime The start of the time period
     * @param endTime The end of the time period
     * @return True if generation is in progress, false otherwise
     */
    suspend fun isGenerationInProgress(startTime: Instant, endTime: Instant): Boolean
    
    /**
     * Cancels a pending or in-progress generation request.
     * 
     * @param requestId The ID of the request to cancel
     * @return True if the request was cancelled, false if it couldn't be cancelled
     */
    suspend fun cancelGeneration(requestId: Uuid): Boolean
}