package app.logdate.client.location

import app.logdate.shared.model.Location
import kotlin.time.Instant

/**
 * One position as the platform reported it: where, when the fix was taken, and how precise it
 * claims to be. A fix can be older than the moment it is read, so [observedAt] is the time the
 * position was true, not the time it was asked for.
 */
data class LocationFix(
    val location: Location,
    val observedAt: Instant,
    /** Radius in meters the platform is about 68% confident the position falls within; null when unknown. */
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val isMock: Boolean = false,
) {
    /** The evidence [app.logdate.client.location.history.LocationTracker.logLocation] stores with the position. */
    fun evidence(): Map<String, Any> =
        buildMap {
            accuracyMeters?.let { put("accuracyMeters", it) }
            speedMetersPerSecond?.let { put("speedMetersPerSecond", it) }
            bearingDegrees?.let { put("bearingDegrees", it) }
            put("isMock", isMock)
        }
}
