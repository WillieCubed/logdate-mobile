package app.logdate.client.repository.transcription

import kotlin.time.Instant
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
     * Structured transcript document containing timed segments and source
     * metadata. Legacy rows may have only [text] until they are backfilled.
     */
    val transcriptDocument: TranscriptDocument? = null,
    /**
     * The status of the transcription process.
     */
    val status: TranscriptionStatus,
    /**
     * BCP-47 language tag associated with the transcript when known.
     */
    val language: String? = transcriptDocument?.language,
    /**
     * Highest-authority source represented by the current transcript document.
     */
    val source: TranscriptSource? = transcriptDocument?.segments?.maxByOrNull { it.source.ordinal }?.source,
    /**
     * Recognition model that produced the current transcript when reported by the engine.
     */
    val modelId: String? = null,
    /**
     * Durable document revision used to reconcile local, cloud, and synced transcript updates.
     */
    val revision: Int = transcriptDocument?.revision ?: 0,
    /**
     * True when a LogDate Cloud pass has improved or produced this transcript.
     */
    val isCloudEnhanced: Boolean = false,
    /**
     * Number of detected or assigned speakers represented by this transcript.
     */
    val speakerCount: Int = transcriptDocument?.speakers?.size ?: 0,
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
    val id: Uuid,
)

/**
 * Enum representing the possible states of a transcription.
 */
enum class TranscriptionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}
