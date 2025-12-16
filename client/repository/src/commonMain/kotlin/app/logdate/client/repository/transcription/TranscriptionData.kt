package app.logdate.client.repository.transcription

import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Data class representing the transcription of an audio note.
 */
data class TranscriptionData(
    /**
     * The ID of the audio note this transcription belongs to.
     */
    val noteId: Uuid,
    
    /**
     * The transcribed text content.
     * This may be null if the transcription is still in progress or failed.
     */
    val text: String?,
    
    /**
     * The status of the transcription process.
     */
    val status: TranscriptionStatus,
    
    /**
     * Error message if the transcription failed.
     */
    val errorMessage: String? = null,
    
    /**
     * Timestamp when the transcription was created.
     */
    val created: Instant,
    
    /**
     * Timestamp when the transcription was last updated.
     */
    val lastUpdated: Instant,
    
    /**
     * Unique identifier for this transcription.
     */
    val id: Uuid
)

/**
 * Enum representing the possible states of a transcription.
 */
enum class TranscriptionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}