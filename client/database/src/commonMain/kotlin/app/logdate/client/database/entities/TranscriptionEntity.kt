package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Entity for storing transcriptions of audio notes.
 *
 * This entity is linked to a [AudioNoteEntity] via the [noteId] field.
 * The transcription can be in multiple states:
 * - PENDING: Transcription has been requested but not started
 * - IN_PROGRESS: Transcription is currently being processed
 * - COMPLETED: Transcription is complete and available
 * - FAILED: Transcription failed
 */
@Entity(
    tableName = "transcriptions",
    foreignKeys = [
        ForeignKey(
            entity = AudioNoteEntity::class,
            parentColumns = ["uid"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("noteId", unique = true),
    ],
)
data class TranscriptionEntity(
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
     * Serialized structured transcript document. Kept nullable so legacy rows
     * and plain-text-only updates remain valid.
     */
    val documentJson: String? = null,
    /**
     * BCP-47 language tag associated with the transcript when known.
     */
    val language: String? = null,
    /**
     * Serialized source name for the highest-authority transcript pass.
     */
    val source: String? = null,
    /**
     * Recognition model that produced the current transcript when reported by the engine.
     */
    val modelId: String? = null,
    /**
     * Durable transcript document revision.
     */
    val revision: Int = 0,
    /**
     * True when a LogDate Cloud pass has improved or produced this transcript.
     */
    val isCloudEnhanced: Boolean = false,
    /**
     * Number of detected or assigned speakers represented by this transcript.
     */
    val speakerCount: Int = 0,
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
    @PrimaryKey
    val id: Uuid = Uuid.random(),
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
