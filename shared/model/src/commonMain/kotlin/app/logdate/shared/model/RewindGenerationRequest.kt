package app.logdate.shared.model

import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * A request to generate a rewind for a specific time period.
 */
data class RewindGenerationRequest(
    /**
     * Unique identifier for this generation request.
     */
    val id: Uuid,
    
    /**
     * Start time of the period to generate a rewind for.
     */
    val startTime: Instant,
    
    /**
     * End time of the period to generate a rewind for.
     */
    val endTime: Instant,
    
    /**
     * When this request was created.
     */
    val requestTime: Instant,
    
    /**
     * Current status of the generation request.
     */
    val status: Status,
    
    /**
     * Optional details about the request, like error information.
     */
    val details: String? = null,
    
    /**
     * The ID of the generated rewind, if completed successfully.
     */
    val rewindId: Uuid? = null
) {
    /**
     * Possible statuses for a rewind generation request.
     */
    enum class Status {
        /**
         * Request is queued but not yet being processed.
         */
        PENDING,
        
        /**
         * Request is actively being processed.
         */
        PROCESSING,
        
        /**
         * Generation completed successfully.
         */
        COMPLETED,
        
        /**
         * Generation failed.
         */
        FAILED,
        
        /**
         * Generation was cancelled.
         */
        CANCELLED
    }
}