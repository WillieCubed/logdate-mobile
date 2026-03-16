package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.JournalNote
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A semantically coherent cluster of notes representing a distinct experience.
 *
 * A Moment is NOT a 1:1 mapping from notes. A single text note written at 10pm
 * might be split into multiple moments if it describes events from different times.
 * Conversely, multiple photos taken at the same place form a single moment.
 */
data class Moment(
    val id: Uuid,
    /**
     * Contextual label: "At Blue Bottle Coffee", "That evening", "Morning run".
     */
    val label: String,
    /**
     * Estimated time range this moment covers (may differ from note timestamps).
     */
    val estimatedStart: Instant,
    val estimatedEnd: Instant,
    /**
     * The notes that provide evidence for this moment.
     */
    val sourceNotes: List<JournalNote>,
    /**
     * Text fragments extracted from notes relevant to this moment
     * (may be subsets of full notes).
     */
    val textFragments: List<MomentTextFragment>,
    /**
     * Media items associated with this moment.
     */
    val media: List<MomentMedia>,
    /**
     * Audio items associated with this moment.
     */
    val audio: List<MomentAudio>,
    /**
     * Places associated with this moment.
     */
    val places: List<TimelinePlaceVisit>,
    /**
     * People mentioned in this moment.
     */
    val people: List<String>,
    /**
     * How this moment was inferred.
     */
    val inferenceSource: MomentInferenceSource,
)

data class MomentTextFragment(
    val text: String,
    val sourceNoteId: Uuid,
)

data class MomentMedia(
    val uri: String,
    val isVideo: Boolean,
    val sourceNoteId: Uuid,
)

data class MomentAudio(
    val uri: String,
    val durationMs: Long,
    val sourceNoteId: Uuid,
)

enum class MomentInferenceSource {
    /**
     * AI analyzed content semantically and created moment boundaries.
     */
    AI_INFERRED,

    /**
     * Heuristic: grouped by time-of-day buckets.
     */
    TIME_OF_DAY_FALLBACK,

    /**
     * Single note with clear moment identity (e.g., photo at a location).
     */
    DIRECT_MAPPING,
}
