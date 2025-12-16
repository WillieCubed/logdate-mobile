package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Entity for storing transcriptions of audio notes.
 * 
 * This entity is linked to a [VoiceNoteEntity] via the [noteId] field.
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
            entity = VoiceNoteEntity::class,
            parentColumns = ["uid"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("noteId", unique = true)
    ]
)
data class TranscriptionEntity(
    /**
     * The ID of the voice note this transcription belongs to.
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
    @PrimaryKey
    val id: Uuid = Uuid.random()
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