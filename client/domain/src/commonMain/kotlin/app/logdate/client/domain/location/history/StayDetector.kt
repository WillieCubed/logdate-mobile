package app.logdate.client.domain.location.history

import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.TravelMode

/**
 * A stretch of precise samples where someone stayed in one place.
 *
 * [range] covers every sample from arrival to departure, including brief drift away from the spot;
 * [anchors] are the samples that actually sat at the spot and decide where it is.
 */
internal data class DetectedStay(
    val range: IntRange,
    val anchors: List<Int>,
) {
    fun center(points: List<LocationObservation>): Pair<Double, Double> = weightedCenter(anchors.map(points::get))
}

/**
 * Finds stays in a time-ordered run of precise samples with no recording gaps.
 *
 * A stay grows from a starting sample while new samples land near its centre. Samples that wander
 * off and come back after at most [HistoryReconstructionParameters.excursionTolerance] are kept as drift.
 * A candidate only counts when it lasts [HistoryReconstructionParameters.minimumStay], its centre
 * holds still (a slow walk keeps moving it), and the recorded activity is not a vehicle.
 */
internal class StayDetector(
    private val parameters: HistoryReconstructionParameters,
) {
    fun detect(points: List<LocationObservation>): List<DetectedStay> {
        val stays = mutableListOf<DetectedStay>()
        var start = 0
        while (start < points.size) {
            val stay = grow(points, start)?.let { trim(points, it) }?.takeIf { qualifies(points, it) }
            if (stay == null) {
                start++
            } else {
                stays += stay
                start = stay.range.last + 1
            }
        }
        return stays
    }

    fun belongs(
        center: Pair<Double, Double>,
        sample: LocationObservation,
    ): Boolean =
        center.distanceTo(sample) <=
            parameters.stayRadiusMeters + minOf(sample.accuracyMeters?.toDouble() ?: 0.0, parameters.accuracyAllowanceMeters)

    private fun grow(
        points: List<LocationObservation>,
        start: Int,
    ): DetectedStay? {
        val anchors = mutableListOf(start)
        val center = CenterAccumulator().apply { add(points[start]) }
        var index = start + 1
        while (index < points.size) {
            if (belongs(center.value, points[index])) {
                anchors += index
                center.add(points[index])
                index++
                continue
            }
            index = returnAfterDrift(points, center.value, index) ?: break
        }
        return DetectedStay(start..anchors.last(), anchors).takeIf { lasts(points, it) }
    }

    /**
     * The index where samples come back to the stay, provided the ones away from it span no longer
     * than the excursion tolerance. One stray fix between two at-home fixes spans no time at all,
     * however far apart the phone happened to sample.
     */
    private fun returnAfterDrift(
        points: List<LocationObservation>,
        center: Pair<Double, Double>,
        firstAway: Int,
    ): Int? {
        val leftAt = points[firstAway].timestamp
        var index = firstAway + 1
        while (index < points.size) {
            if (belongs(center, points[index])) return index
            if (points[index].timestamp - leftAt > parameters.excursionTolerance) return null
            index++
        }
        return null
    }

    /** Drops arrival and departure samples that the finished stay's centre shows were still on the way. */
    private fun trim(
        points: List<LocationObservation>,
        stay: DetectedStay,
    ): DetectedStay? {
        val center = stay.center(points)
        val anchors = stay.anchors.dropWhile { !belongs(center, points[it]) }.dropLastWhile { !belongs(center, points[it]) }
        if (anchors.isEmpty()) return null
        return DetectedStay(anchors.first()..anchors.last(), anchors)
    }

    private fun qualifies(
        points: List<LocationObservation>,
        stay: DetectedStay,
    ): Boolean = lasts(points, stay) && !riding(points, stay) && !drifting(points, stay)

    private fun lasts(
        points: List<LocationObservation>,
        stay: DetectedStay,
    ): Boolean = points[stay.anchors.last()].timestamp - points[stay.anchors.first()].timestamp >= parameters.minimumStay

    private fun riding(
        points: List<LocationObservation>,
        stay: DetectedStay,
    ): Boolean {
        val activities = stay.anchors.map { points[it].activity }.filter { it != TravelMode.UNKNOWN }
        return activities.isNotEmpty() && activities.count { it in RIDING_MODES } * 2 > activities.size
    }

    /** A slow walk keeps a "stay" centred on the walker; its early and late samples sit apart. */
    private fun drifting(
        points: List<LocationObservation>,
        stay: DetectedStay,
    ): Boolean {
        if (stay.anchors.size < MINIMUM_SAMPLES_FOR_DRIFT) return false
        val third = stay.anchors.size / 3
        val early = weightedCenter(stay.anchors.take(third).map(points::get))
        val late = weightedCenter(stay.anchors.takeLast(third).map(points::get))
        return early.distanceTo(late) > parameters.stayRadiusMeters
    }

    private companion object {
        const val MINIMUM_SAMPLES_FOR_DRIFT = 6
    }
}
