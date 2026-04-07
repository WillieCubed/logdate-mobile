package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One detected ambient sound (a "Bird", "Rain", "Car passing by", etc.) on
 * an audio note. A single note can carry many tags — they're written by the
 * on-device tagger after the user finishes recording.
 *
 * Cascade-deletes with the parent [AudioNoteEntity] so a removed audio note
 * leaves no orphan tags behind.
 */
@Entity(
    tableName = "audio_tags",
    foreignKeys = [
        ForeignKey(
            entity = AudioNoteEntity::class,
            parentColumns = ["uid"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("noteId"),
        Index("soundName"),
    ],
)
data class AudioTagEntity(
    /** The audio note this tag belongs to. */
    val noteId: Uuid,
    /** Human-readable AudioSet class name, e.g. "Bird", "Rain". */
    val soundName: String,
    /** Model probability for this detection in the range [0, 1]. */
    val confidence: Float,
    /** Offset from the start of the recording where the sound began. */
    val startMs: Long,
    /** How long the sound was sustained, summed across overlapping windows. */
    val durationMs: Long,
    /** When this tag was written. */
    val created: Instant,
    @PrimaryKey
    val id: Uuid = Uuid.random(),
)
