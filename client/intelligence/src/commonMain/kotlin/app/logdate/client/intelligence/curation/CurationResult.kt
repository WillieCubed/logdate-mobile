package app.logdate.client.intelligence.curation

import kotlin.uuid.Uuid

/**
 * Output of one curation pass.
 *
 * [perBeat] maps a story-beat index to the chosen candidates for that beat (sorted by
 * score, capped by config). [freeAgents] are high-significance items that didn't fall
 * inside any beat's time window — the structural-panel pool. [sigByMediaUid] is the
 * lookup the sequencer uses to stamp `significanceScore` onto emitted
 * `RewindContent.Image` / `Video` panels.
 */
data class CurationResult(
    val perBeat: Map<Int, List<MediaCandidate>>,
    val freeAgents: List<MediaCandidate>,
    val rejected: List<RejectedCandidate>,
    val sigByMediaUid: Map<Uuid, Float>,
) {
    companion object {
        /** Empty result — used when curation has no input or every candidate was rejected. */
        val EMPTY: CurationResult =
            CurationResult(
                perBeat = emptyMap(),
                freeAgents = emptyList(),
                rejected = emptyList(),
                sigByMediaUid = emptyMap(),
            )
    }
}
