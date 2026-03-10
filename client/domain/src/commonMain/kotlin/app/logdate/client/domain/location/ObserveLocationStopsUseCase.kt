package app.logdate.client.domain.location

import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Aggregates consecutive location pings into timeline-like stops.
 */
class ObserveLocationStopsUseCase(
    private val observeLocationHistoryUseCase: ObserveLocationHistoryUseCase,
    private val stopRadiusMeters: Double = 100.0,
    private val maxGapBetweenSamples: Duration = 30.minutes,
) {
    operator fun invoke(): Flow<List<LocationStop>> = observeLocationHistoryUseCase().map(::aggregateStops)

    internal fun aggregateStops(history: List<LocationHistoryItem>): List<LocationStop> {
        if (history.isEmpty()) {
            return emptyList()
        }

        val sortedHistory = history.sortedBy { it.timestamp }
        val groupedStops = mutableListOf<List<LocationHistoryItem>>()
        var currentGroup = mutableListOf(sortedHistory.first())

        sortedHistory.drop(1).forEach { item ->
            val previous = currentGroup.last()
            val gap = item.timestamp - previous.timestamp
            val distance =
                calculateDistanceMeters(
                    lat1 = previous.location.latitude,
                    lon1 = previous.location.longitude,
                    lat2 = item.location.latitude,
                    lon2 = item.location.longitude,
                )

            if (gap <= maxGapBetweenSamples && distance <= stopRadiusMeters) {
                currentGroup.add(item)
            } else {
                groupedStops.add(currentGroup)
                currentGroup = mutableListOf(item)
            }
        }

        groupedStops.add(currentGroup)

        return groupedStops
            .map(::toStop)
            .sortedByDescending { it.endTime }
    }

    private fun toStop(group: List<LocationHistoryItem>): LocationStop {
        val latitude = group.map { it.location.latitude }.average()
        val longitude = group.map { it.location.longitude }.average()
        val altitude = group.map { it.location.altitude.value }.average()
        val first = group.first()
        val last = group.last()

        return LocationStop(
            id = "${first.userId}:${first.deviceId}:${first.timestamp.toEpochMilliseconds()}:${last.timestamp.toEpochMilliseconds()}",
            location =
                Location(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = LocationAltitude(altitude, AltitudeUnit.METERS),
                ),
            startTime = first.timestamp,
            endTime = last.timestamp,
            sampleCount = group.size,
        )
    }

    private fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadius = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * PI / 180) * cos(lat2 * PI / 180) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
