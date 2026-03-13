package app.logdate.shared.model

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A physical location on the planet Earth.
 *
 * For locations with semantic meaning, see [Place].
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitude: LocationAltitude,
) {
    /**
     * Calculates the great-circle distance in meters to another location using the
     * Haversine formula. Useful for deduplicating GPS micro-jitter.
     */
    fun distanceTo(other: Location): Double {
        val earthRadius = 6371000.0 // meters
        val lat1 = latitude * PI / 180
        val lat2 = other.latitude * PI / 180
        val dLat = (other.latitude - latitude) * PI / 180
        val dLon = (other.longitude - longitude) * PI / 180

        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}

data class LocationAltitude(
    val value: Double,
    val units: AltitudeUnit,
)

enum class AltitudeUnit(
    val unit: String,
) {
    METERS("m"),
    FEET("ft"),
}
