package app.logdate.client.intelligence.curation

import kotlin.math.abs

/**
 * Picks the final per-beat photo set with diversity constraints.
 *
 * Rules per beat:
 *  - at most [CurationConfig.maxItemsPerBeat] items
 *  - at most one item per `MediaSignals.burstGroupKey` (insurance even though
 *    [PhotoHardFilter] collapses bursts upstream)
 *  - at most two items inside any 5-minute sub-window so a beat doesn't become three
 *    near-identical photos
 *  - LLM-cited items can exceed the per-beat cap up to `maxItemsPerBeat + 2` — being
 *    cited is an explicit "this matters" override of the diversity rule
 *
 * After all beats are filled, a global cap ([CurationConfig.maxTotalMedia]) drops the
 * lowest-scoring items, but every beat with any candidate is guaranteed at least one
 * slot.
 */
class DiversitySelector {
    fun select(
        scoredCandidates: List<MediaCandidate>,
        config: CurationConfig,
    ): SelectionOutcome {
        val perBeat = mutableMapOf<Int, MutableList<MediaCandidate>>()
        val freeAgents = mutableListOf<MediaCandidate>()

        // Group by beat assignment; sort each group by score (descending) so the picker
        // walks best-first.
        val byBeat: Map<Int?, List<MediaCandidate>> =
            scoredCandidates
                .groupBy { it.assignedBeatIndex }
                .mapValues { (_, list) -> list.sortedByDescending { it.derivedScores?.total ?: 0f } }

        for ((beatIdx, candidatesInBeat) in byBeat) {
            if (beatIdx == null) {
                // Free agents: any candidate not bound to a beat that meets the threshold,
                // plus all LLM-cited items regardless of score.
                for (candidate in candidatesInBeat) {
                    if (candidate.isLLMCited ||
                        (candidate.derivedScores?.total ?: 0f) >= config.minSignificanceForFreeAgent
                    ) {
                        freeAgents.add(candidate)
                    }
                }
                continue
            }
            perBeat[beatIdx] = pickForBeat(candidatesInBeat, config)
        }

        val capped = enforceGlobalCap(perBeat, freeAgents, config)
        return capped
    }

    private fun pickForBeat(
        sortedDesc: List<MediaCandidate>,
        config: CurationConfig,
    ): MutableList<MediaCandidate> {
        val picked = mutableListOf<MediaCandidate>()
        val usedBurstKeys = mutableSetOf<String>()
        val pickedTimestamps = mutableListOf<Long>()

        val softCap = config.maxItemsPerBeat
        val hardCap = softCap + 2 // citation override headroom

        for (c in sortedDesc) {
            if (picked.size >= hardCap) break
            val overSoftCap = picked.size >= softCap
            if (overSoftCap && !c.isLLMCited) continue

            val burstKey = c.signals.burstGroupKey
            if (burstKey != null && burstKey in usedBurstKeys) continue

            val ts = c.media.timestamp.toEpochMilliseconds()
            val nearCount = pickedTimestamps.count { abs(it - ts) <= SUB_WINDOW_MS }
            if (nearCount >= 2 && !c.isLLMCited) continue

            picked.add(c)
            if (burstKey != null) usedBurstKeys.add(burstKey)
            pickedTimestamps.add(ts)
        }
        return picked
    }

    /**
     * Enforces the global [CurationConfig.maxTotalMedia] cap. Every beat with any
     * candidate keeps at least one slot; remaining slots fill in by global score.
     */
    private fun enforceGlobalCap(
        perBeat: Map<Int, MutableList<MediaCandidate>>,
        freeAgents: List<MediaCandidate>,
        config: CurationConfig,
    ): SelectionOutcome {
        val cap = config.maxTotalMedia
        val totalChosen = perBeat.values.sumOf { it.size }
        if (totalChosen <= cap) {
            return SelectionOutcome(perBeat = perBeat.mapValues { (_, v) -> v.toList() }, freeAgents = freeAgents)
        }

        // Trim from the lowest-scoring tail across all beats, but never drop a beat's
        // last item. Free agents are not counted against the cap — they fuel structural
        // panels, not story beats.
        val beatItems: MutableMap<Int, MutableList<MediaCandidate>> = perBeat.mapValues { it.value.toMutableList() }.toMutableMap()
        var overage = totalChosen - cap

        // Build a removal queue: ordered ascending by score, but a beat's last surviving
        // item is filtered out each pass so we keep at least one per beat.
        while (overage > 0) {
            val droppable: List<Triple<Int, Int, Float>> =
                beatItems.flatMap { (beatIdx, items) ->
                    if (items.size <= 1) {
                        emptyList()
                    } else {
                        items.mapIndexed { i, c -> Triple(beatIdx, i, c.derivedScores?.total ?: 0f) }
                    }
                }
            if (droppable.isEmpty()) break
            val (beatIdx, i, _) = droppable.minBy { it.third }
            beatItems.getValue(beatIdx).removeAt(i)
            overage--
        }

        return SelectionOutcome(perBeat = beatItems.mapValues { it.value.toList() }, freeAgents = freeAgents)
    }

    /**
     * Final shape of one [DiversitySelector.select] pass.
     */
    data class SelectionOutcome(
        val perBeat: Map<Int, List<MediaCandidate>>,
        val freeAgents: List<MediaCandidate>,
    )

    private companion object {
        private const val SUB_WINDOW_MS = 5L * 60L * 1000L
    }
}
