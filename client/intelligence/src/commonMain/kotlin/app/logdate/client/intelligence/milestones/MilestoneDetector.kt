package app.logdate.client.intelligence.milestones

import kotlin.time.Instant

/**
 * Detects significant life events from existing user data so the rewind pipeline can
 * generate event-window rewinds outside the normal weekly cadence.
 *
 * Detectors are best-effort: returning null means "nothing significant happened that I
 * can detect right now, try again next worker run". Each detector handles one kind of
 * milestone and decides its own time window.
 *
 * The intelligence layer doesn't know how the milestone gets persisted — it only
 * surfaces a [MilestoneCandidate]. The orchestration layer (the worker) is what calls
 * the rewind generator with the candidate's window and tags the resulting rewind.
 */
interface MilestoneDetector {
    /** Returns a candidate to act on, or null when nothing significant has been detected. */
    suspend fun detect(now: Instant): MilestoneCandidate?
}

/**
 * One detected significant life event with the time window the rewind should cover.
 *
 * @property kind A coarse machine-readable category for the milestone, used by the
 *   overview list to pick a card treatment.
 * @property startTime Inclusive start of the rewind window.
 * @property endTime Inclusive end of the rewind window.
 * @property summary One-line human-readable summary the overview card uses as a label.
 *   Phrased as a noun-like phrase ("Your move", "A new chapter") rather than a sentence.
 */
data class MilestoneCandidate(
    val kind: MilestoneKind,
    val startTime: Instant,
    val endTime: Instant,
    val summary: String,
)

/**
 * The coarse category of a detected milestone.
 *
 * Stored on the rewind's metadata as the prefix of `milestones[0]` so the overview
 * list can derive a distinct visual treatment without parsing free text. Future
 * detectors will add new variants here.
 */
enum class MilestoneKind {
    /** The user's primary location centroid jumped meaningfully and stayed put. */
    LOCATION_CHANGE,
}

/**
 * Encodes a [MilestoneCandidate] into the string the rewind metadata stores at
 * `milestones[0]`. Format: `<kind-name>:<summary>`. Decoded by the overview view
 * model so cards know which kind of milestone they represent.
 */
fun MilestoneCandidate.toMetadataSignal(): String = "${kind.name}:$summary"

/**
 * Inverse of [toMetadataSignal] — null when the input doesn't carry a recognizable
 * milestone prefix, in which case the rewind should be treated as a normal weekly one.
 */
fun parseMilestoneSignal(raw: String?): ParsedMilestoneSignal? {
    if (raw.isNullOrBlank()) return null
    val separator = raw.indexOf(':')
    if (separator <= 0 || separator == raw.lastIndex) return null
    val kindName = raw.substring(0, separator)
    val summary = raw.substring(separator + 1)
    val kind = runCatching { MilestoneKind.valueOf(kindName) }.getOrNull() ?: return null
    return ParsedMilestoneSignal(kind = kind, summary = summary)
}

/** A milestone signal pulled out of `RewindMetadata.milestones[0]`. */
data class ParsedMilestoneSignal(
    val kind: MilestoneKind,
    val summary: String,
)
