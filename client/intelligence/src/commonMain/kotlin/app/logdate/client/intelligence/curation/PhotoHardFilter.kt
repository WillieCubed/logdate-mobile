package app.logdate.client.intelligence.curation

import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drops candidates that are obviously not Rewind material — screenshots, doc scans,
 * tiny thumbnails, unsupported MIME types, and burst duplicates.
 *
 * Every rule is signal-driven: when a signal is null the rule is skipped, never
 * applied incorrectly. That keeps the filter useful on platforms whose extractor
 * returns partial data (current desktop, current iOS pre-PHAsset wiring).
 */
class PhotoHardFilter {
    /**
     * Returns the candidates that survive the filter and a parallel list of rejection
     * records for everything dropped.
     */
    fun filter(
        candidates: List<MediaCandidate>,
        config: CurationConfig,
    ): FilterOutcome {
        val survivors = mutableListOf<MediaCandidate>()
        val rejected = mutableListOf<RejectedCandidate>()

        for (candidate in candidates) {
            val reasons = reasonsToReject(candidate, config)
            if (reasons.isEmpty()) {
                survivors.add(candidate)
            } else {
                rejected.add(RejectedCandidate(candidate.media, reasons))
            }
        }

        // Burst dedup runs after per-item rules so a screenshot that happens to share a
        // burst with normal photos still gets the SCREENSHOT verdict instead of slipping
        // through as the burst representative.
        val (afterBurstDedup, burstRejects) = collapseBursts(survivors, config)
        return FilterOutcome(
            survivors = afterBurstDedup,
            rejected = rejected + burstRejects,
        )
    }

    private fun reasonsToReject(
        candidate: MediaCandidate,
        config: CurationConfig,
    ): List<RejectReason> {
        val reasons = mutableListOf<RejectReason>()
        val s = candidate.signals

        if (config.excludeScreenshots && s.isLikelyScreenshot) reasons.add(RejectReason.SCREENSHOT)
        if (config.excludeDocScans && s.isLikelyDocumentScan) reasons.add(RejectReason.DOC_SCAN)

        // Resolution rule only fires when we have concrete pixel dimensions to compare —
        // null width/height = "unknown", which we accept rather than reject.
        val w = s.widthPx
        val h = s.heightPx
        if (w != null && h != null && (w < config.minResolutionPx || h < config.minResolutionPx)) {
            reasons.add(RejectReason.BELOW_MIN_RESOLUTION)
        }

        // MIME allowlist — only enforced when the MIME is known.
        if (s.mimeType != null && s.mimeType !in SUPPORTED_MIMES) {
            reasons.add(RejectReason.UNSUPPORTED_MIME)
        }

        return reasons
    }

    /**
     * Collapses burst groups down to a single representative — the highest-resolution
     * member, or (if all members share dimensions / dimensions are null) the first by
     * timestamp. Items with no [MediaSignals.burstGroupKey] but timestamps within
     * [CurationConfig.burstWindowMs] of each other are also treated as a burst, since
     * not every platform surfaces an explicit burst id.
     */
    private fun collapseBursts(
        candidates: List<MediaCandidate>,
        config: CurationConfig,
    ): Pair<List<MediaCandidate>, List<RejectedCandidate>> {
        if (candidates.isEmpty()) return emptyList<MediaCandidate>() to emptyList()

        val explicitGroups = candidates.filter { it.signals.burstGroupKey != null }.groupBy { it.signals.burstGroupKey!! }
        val ungrouped = candidates.filter { it.signals.burstGroupKey == null }

        val survivors = mutableListOf<MediaCandidate>()
        val rejected = mutableListOf<RejectedCandidate>()

        for ((_, members) in explicitGroups) {
            val (keeper, dropped) = pickRepresentative(members)
            survivors.add(keeper)
            dropped.forEach { rejected.add(RejectedCandidate(it.media, listOf(RejectReason.BURST_DUPLICATE))) }
        }

        // Time-clustering pass on the ungrouped pool — sort by timestamp, build runs of
        // candidates within `burstWindowMs` of the previous one.
        val sorted = ungrouped.sortedBy { it.media.timestamp.toEpochMilliseconds() }
        var current = mutableListOf<MediaCandidate>()
        var lastTs = Long.MIN_VALUE
        val window = config.burstWindowMs.milliseconds.inWholeMilliseconds
        for (c in sorted) {
            val ts = c.media.timestamp.toEpochMilliseconds()
            if (current.isEmpty() || abs(ts - lastTs) <= window) {
                current.add(c)
            } else {
                emitRun(current, survivors, rejected)
                current = mutableListOf(c)
            }
            lastTs = ts
        }
        if (current.isNotEmpty()) emitRun(current, survivors, rejected)

        return survivors to rejected
    }

    private fun emitRun(
        run: List<MediaCandidate>,
        survivors: MutableList<MediaCandidate>,
        rejected: MutableList<RejectedCandidate>,
    ) {
        if (run.size == 1) {
            survivors.add(run.single())
            return
        }
        val (keeper, dropped) = pickRepresentative(run)
        survivors.add(keeper)
        dropped.forEach { rejected.add(RejectedCandidate(it.media, listOf(RejectReason.BURST_DUPLICATE))) }
    }

    private fun pickRepresentative(members: List<MediaCandidate>): Pair<MediaCandidate, List<MediaCandidate>> {
        val sorted =
            members.sortedWith(
                compareByDescending<MediaCandidate> { c ->
                    val w = c.signals.widthPx?.toLong() ?: 0L
                    val h = c.signals.heightPx?.toLong() ?: 0L
                    w * h
                }.thenBy { it.media.timestamp.toEpochMilliseconds() },
            )
        val keeper = sorted.first()
        return keeper to sorted.drop(1)
    }

    /**
     * Outcome of one filter pass. [survivors] preserves the input order minus rejections.
     */
    data class FilterOutcome(
        val survivors: List<MediaCandidate>,
        val rejected: List<RejectedCandidate>,
    )

    companion object {
        /** MIME types accepted into a Rewind. Anything else is dropped as `UNSUPPORTED_MIME`. */
        val SUPPORTED_MIMES =
            setOf(
                "image/jpeg",
                "image/png",
                "image/heic",
                "image/heif",
                "image/webp",
                "video/mp4",
                "video/quicktime",
                "video/x-m4v",
            )
    }
}
