package app.logdate.client.intelligence.curation

import app.logdate.client.repository.media.IndexedMedia

/**
 * One photo / video being considered for inclusion in a Rewind.
 *
 * Wraps an [IndexedMedia] with the platform-extracted [MediaSignals], the per-stage
 * [ScoreBreakdown] once scored, and the beat index it was assigned to (or `null` if it
 * landed in the free-agent pool for structural panels). The wrapper is rebuilt per
 * curation pass — none of this is persisted.
 */
data class MediaCandidate(
    val media: IndexedMedia,
    val signals: MediaSignals,
    val derivedScores: ScoreBreakdown? = null,
    val assignedBeatIndex: Int? = null,
    val isLLMCited: Boolean = false,
)

/**
 * Per-platform signals about a media item used by the curator's hard filter and scorer.
 *
 * Every field is nullable. The scorer and filter degrade gracefully — a null signal
 * contributes 0 to its sub-score and skips its filter rule, so a platform whose
 * extractor returns mostly nulls (current desktop) still gets a usable curation pass
 * driven by the cross-platform signals (people proximity, journal proximity, time
 * clustering).
 */
data class MediaSignals(
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val sizeBytes: Long? = null,
    val isLikelyScreenshot: Boolean = false,
    val isLikelyDocumentScan: Boolean = false,
    val isLikelyBurstMember: Boolean = false,
    /** Stable group key shared by all members of the same burst. Null for non-bursts. */
    val burstGroupKey: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
)

/**
 * Breakdown of how a candidate's [total] significance score was assembled.
 *
 * Kept as a struct (rather than a single float) so debugging / telemetry can explain
 * why a photo did or didn't make it into a Rewind. [total] is 0..100 clamped.
 */
data class ScoreBreakdown(
    val total: Float,
    val narrativeEvidence: Float = 0f,
    val peopleProximity: Float = 0f,
    val locationNovelty: Float = 0f,
    val timeClusterDensity: Float = 0f,
    val journalProximity: Float = 0f,
    val mediaIntrinsic: Float = 0f,
    val penalty: Float = 0f,
)

/**
 * A candidate that the hard filter rejected, with reasons. Kept for telemetry and so
 * a future "Why didn't my photo show up?" UI can explain itself.
 */
data class RejectedCandidate(
    val media: IndexedMedia,
    val reasons: List<RejectReason>,
)

/**
 * Why the hard filter discarded a candidate.
 */
enum class RejectReason {
    SCREENSHOT,
    DOC_SCAN,
    BELOW_MIN_RESOLUTION,
    UNSUPPORTED_MIME,
    BURST_DUPLICATE,
    BELOW_MIN_SIGNIFICANCE,
    NOT_IN_PERIOD,
}
