package app.logdate.model

/**
 * A physical location on the planet Earth.
 *
 * For locations with semantic meaning, see [Place].
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitude: LocationAltitude,
)

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