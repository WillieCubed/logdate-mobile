package app.logdate.client.domain.location.history

import app.logdate.shared.model.location.HistoryGap
import app.logdate.shared.model.location.JourneyLeg
import app.logdate.shared.model.location.LocationDayItem
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.TravelMode
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rebuilds a day of visits and journeys from raw location samples.
 *
 * Each recording device is reconstructed on its own. Its samples are first cut wherever the phone
 * stopped recording for longer than [HistoryReconstructionParameters.maximumGap]; those cuts are
 * shown as gaps rather than guessed across. Within each recorded stretch, [SegmentAssembler]
 * separates the time someone stayed put from the time they travelled, tolerating the position noise
 * real phones produce. Fixes without a reported accuracy only shape a stretch that has no
 * precise fixes at all.
 *
 * Item identity comes from evidence: a visit or journey is named after its first precise sample, so
 * the same recordings always produce the same ids, and edits keyed to a sample keep finding it.
 */
class ReconstructLocationDay(
    private val inference: TravelModeInferenceProvider = RecordedTravelModeInferenceProvider(),
    private val parameters: HistoryReconstructionParameters = HistoryReconstructionParameters(),
) {
    private val assembler = SegmentAssembler(parameters)

    operator fun invoke(observations: List<LocationObservation>): List<LocationDayItem> =
        observations
            .filter { it.hasValidCoordinates() }
            .distinctBy { Triple(it.ownerId, it.deviceId, it.id) }
            .groupBy { it.ownerId to it.deviceId }
            .flatMap { (_, source) -> reconstructSource(source.sortedWith(compareBy({ it.timestamp }, { it.id }))) }
            .sortedWith(compareBy({ it.start }, { it.id }))

    private fun reconstructSource(source: List<LocationObservation>): List<LocationDayItem> {
        val preciseIds = preciseSamples(source, parameters)
        val segments = splitAtGaps(source)
        return buildList {
            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    val previous = segments[index - 1].last()
                    val next = segment.first()
                    add(HistoryGap("gap:${previous.id}:${next.id}", previous.timestamp, next.timestamp))
                }
                addAll(withConnectors(assembler.assemble(segment, usableSamples(segment, preciseIds))))
            }
        }
    }

    /** Unrated fixes only shape a stretch when nothing more precise was recorded during it. */
    private fun usableSamples(
        segment: List<LocationObservation>,
        preciseIds: Set<String>,
    ): Set<String> = if (segment.any { it.id in preciseIds }) preciseIds else preciseSamples(segment, parameters, acceptUnrated = true)

    private fun splitAtGaps(source: List<LocationObservation>): List<List<LocationObservation>> {
        val segments = mutableListOf<MutableList<LocationObservation>>()
        source.forEach { sample ->
            val current = segments.lastOrNull()
            if (current == null || sample.timestamp - current.last().timestamp > parameters.maximumGap) {
                segments += mutableListOf(sample)
            } else {
                current += sample
            }
        }
        return segments
    }

    private fun toItem(part: SegmentPart): LocationDayItem {
        val timed = part.timed
        val first = timed.first()
        val id = "${first.ownerId}:${first.deviceId}:${first.id}"
        val evidence = (part.precise + part.vague).map { it.id }
        if (!part.isVisit) {
            val route = part.precise.ifEmpty { part.vague }
            val mode = inference.infer(route).firstOrNull()?.mode ?: TravelMode.UNKNOWN
            return JourneyLeg(id, first.timestamp, timed.last().timestamp, evidence, mode, route)
        }
        val (latitude, longitude) = weightedCenter(part.anchors)
        return PlaceVisit(id, first.timestamp, timed.last().timestamp, evidence, latitude, longitude, part.confirmed)
    }

    /** Two visits with nothing recorded between them were still joined by some trip. */
    private fun withConnectors(parts: List<SegmentPart>): List<LocationDayItem> =
        buildList {
            parts.forEachIndexed { index, part ->
                val previous = parts.getOrNull(index - 1)
                val item = toItem(part)
                val previousItem = lastOrNull()
                if (previous != null &&
                    previous.isVisit &&
                    part.isVisit &&
                    previousItem is PlaceVisit &&
                    item is PlaceVisit &&
                    previousItem.distanceTo(item) > parameters.stayRadiusMeters
                ) {
                    add(connector(previous.timed.last(), part.timed.first()))
                }
                add(item)
            }
        }

    private fun connector(
        previous: LocationObservation,
        next: LocationObservation,
    ): JourneyLeg {
        val connectorId = "journey:${previous.ownerId}:${previous.deviceId}:${previous.id}:${next.id}"
        // The inferred relation is editable without editing either endpoint visit.
        return JourneyLeg(connectorId, previous.timestamp, next.timestamp, listOf("derived:$connectorId"), TravelMode.UNKNOWN, emptyList())
    }

    private fun PlaceVisit.distanceTo(other: PlaceVisit): Double = geographicDistance(latitude, longitude, other.latitude, other.longitude)

    companion object {
        const val VERSION = 3
    }
}

internal fun distanceMeters(
    a: LocationObservation,
    b: LocationObservation,
): Double = geographicDistance(a.latitude, a.longitude, b.latitude, b.longitude)

internal fun geographicDistance(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val dLat = (lat2 - lat1) * PI / 180
    val dLon = (lon2 - lon1) * PI / 180
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
    return 6371000 * 2 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1 - a).coerceIn(0.0, 1.0)))
}
