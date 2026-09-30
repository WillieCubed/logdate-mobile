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
import kotlin.time.Duration.Companion.minutes

class ReconstructLocationDay(
    private val inference: TravelModeInferenceProvider = RecordedTravelModeInferenceProvider(),
) {
    operator fun invoke(observations: List<LocationObservation>): List<LocationDayItem> =
        observations
            .filter {
                it.latitude.isFinite() &&
                    it.longitude.isFinite() &&
                    it.latitude in -90.0..90.0 &&
                    it.longitude in -180.0..180.0
            }.distinctBy { Triple(it.ownerId, it.deviceId, it.id) }
            .groupBy { it.ownerId to it.deviceId }
            .flatMap { (_, source) -> reconstructSource(source.sortedWith(compareBy({ it.timestamp }, { it.id }))) }
            .sortedWith(compareBy({ it.start }, { it.id }))

    private fun reconstructSource(source: List<LocationObservation>): List<LocationDayItem> {
        if (source.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<LocationObservation>>()
        source.forEach { sample ->
            val previous = groups.lastOrNull()
            if (previous != null && belongsTo(previous, sample)) {
                previous.add(sample)
            } else {
                groups.add(mutableListOf(sample))
            }
        }
        return buildList {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    val previous = groups[index - 1].last()
                    val next = group.first()
                    if (next.timestamp - previous.timestamp > 10.minutes) {
                        add(HistoryGap("gap:${previous.id}:${next.id}", previous.timestamp, next.timestamp))
                    } else if (!isMoving(previous) && !isMoving(next) && distanceMeters(previous, next) > 75) {
                        val connectorId = "journey:${previous.ownerId}:${previous.deviceId}:${previous.id}:${next.id}"
                        add(
                            JourneyLeg(
                                connectorId,
                                previous.timestamp,
                                next.timestamp,
                                // The inferred relation is editable without editing either endpoint visit.
                                listOf("derived:$connectorId"),
                                TravelMode.UNKNOWN,
                                emptyList(),
                            ),
                        )
                    }
                }
                add(toItem(group))
            }
        }
    }

    private fun belongsTo(
        group: List<LocationObservation>,
        next: LocationObservation,
    ): Boolean {
        val anchor = group.first()
        val previous = group.last()
        if (next.timestamp - previous.timestamp > 10.minutes) return false
        if (!credible(next) || !credible(previous)) return false
        if (isMoving(anchor) != isMoving(next)) return false
        return if (isMoving(anchor)) {
            anchor.activity == next.activity
        } else {
            distanceMeters(anchor, next) <= 75.0 + minOf(anchor.accuracyMeters ?: 0f, next.accuracyMeters ?: 0f, 25f)
        }
    }

    private fun toItem(group: List<LocationObservation>): LocationDayItem {
        val first = group.first()
        val last = group.last()
        val evidence = group.map { it.id }
        val id = "${first.ownerId}:${first.deviceId}:${first.id}"
        if (isMoving(first)) {
            return JourneyLeg(
                id,
                first.timestamp,
                last.timestamp,
                evidence,
                inference.infer(group).firstOrNull()?.mode ?: TravelMode.UNKNOWN,
                group,
            )
        }
        return PlaceVisit(
            id,
            first.timestamp,
            last.timestamp,
            evidence,
            group.map { it.latitude }.average(),
            group.map { it.longitude }.average(),
            group.size >= 2 && last.timestamp - first.timestamp >= 2.minutes && group.all(::credible),
        )
    }

    private fun credible(point: LocationObservation): Boolean = point.accuracyMeters?.let { it in 0f..100f } ?: true

    private fun isMoving(point: LocationObservation): Boolean =
        point.activity !in setOf(TravelMode.STILL, TravelMode.UNKNOWN) || (point.speedMetersPerSecond ?: 0f) > 1f

    companion object {
        const val VERSION = 2
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
