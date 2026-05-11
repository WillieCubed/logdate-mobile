package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlin.uuid.Uuid

/**
 * Timestamped transcript segment used for fast search snippets, tap-to-seek,
 * and speaker-aware playback UI. The canonical transcript remains serialized
 * on [TranscriptionEntity.documentJson]; this table is a query-friendly index
 * that can be rebuilt from the document when needed.
 */
@Entity(
    tableName = "transcription_segments",
    primaryKeys = ["noteId", "segmentId"],
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
        Index("noteId", "startMs"),
    ],
)
data class TranscriptionSegmentEntity(
    val noteId: Uuid,
    val segmentId: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val speakerId: String? = null,
    val confidence: Float? = null,
    val source: String,
    val isFinal: Boolean,
    val revision: Int,
) {
    init {
        require(segmentId.isNotBlank()) { "segmentId must not be blank" }
        require(endMs >= startMs) { "Segment endMs must be >= startMs" }
    }
}
