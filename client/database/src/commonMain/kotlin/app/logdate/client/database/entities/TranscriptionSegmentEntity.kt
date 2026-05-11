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
    /** Audio note that owns this transcript segment. */
    val noteId: Uuid,
    /** Stable segment identity from the transcript document. */
    val segmentId: String,
    /** Segment text used for snippets and local search. */
    val text: String,
    /** Segment start timestamp relative to recording start. */
    val startMs: Long,
    /** Segment end timestamp relative to recording start. */
    val endMs: Long,
    /** Optional speaker identity for diarized playback UI. */
    val speakerId: String? = null,
    /** Engine confidence from 0.0 to 1.0 when available. */
    val confidence: Float? = null,
    /** Serialized [app.logdate.client.repository.transcription.TranscriptSource] name. */
    val source: String,
    /** Whether this segment is stable and no longer a draft hypothesis. */
    val isFinal: Boolean,
    /** Transcript document revision that produced this query row. */
    val revision: Int,
) {
    init {
        require(segmentId.isNotBlank()) { "segmentId must not be blank" }
        require(endMs >= startMs) { "Segment endMs must be >= startMs" }
    }
}
