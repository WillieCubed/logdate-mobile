package app.logdate.client.database.entities.rewind

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Entity for rewind generation requests.
 *
 * This entity represents a request to generate a rewind for a specific time period.
 * It tracks the status of the generation process and associated details.
 */
@Entity(
    tableName = "rewind_generation_requests",
    indices = [
        Index(value = ["startTime", "endTime"], unique = true),
        Index(value = ["rewindId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = RewindEntity::class,
            parentColumns = [RewindConstants.COLUMN_UID],
            childColumns = ["rewindId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class RewindGenerationRequestEntity(
    /**
     * Unique identifier for the request.
     */
    @PrimaryKey
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
     * When the request was created.
     */
    val requestTime: Instant,
    
    /**
     * Current status of the request.
     */
    val status: Status,
    
    /**
     * Optional details about the request status (e.g., error information).
     */
    val details: String? = null,
    
    /**
     * The unique identifier of the generated rewind, if completed successfully.
     */
    val rewindId: Uuid? = null,
) {
    /**
     * Possible statuses for a rewind generation request.
     * 
     * This is a duplicate of the Status enum in RewindGenerationRequest to avoid
     * circular dependencies during build.
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