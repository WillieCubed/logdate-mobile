package app.logdate.client.location.settings

import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.serialization.Serializable

/**
 * Settings for location tracking functionality.
 */
@Serializable
data class LocationTrackingSettings(
    /**
     * Whether background location tracking is enabled.
     */
    val backgroundTrackingEnabled: Boolean = false,
    /**
     * Minimum interval between persisted activity samples in minutes.
     */
    val minimumPersistIntervalMinutes: Long = 30,
    /**
     * Capture mode for background activity experiments.
     */
    val captureMode: LocationCaptureMode = LocationCaptureMode.PASSIVE,
    /**
     * Whether optional server nudges are enabled for the optimized background path.
     */
    val serverAssistEnabled: Boolean = false,
    /**
     * Whether to automatically track location when creating journal entries.
     */
    val autoTrackForJournalEntries: Boolean = true,
    /**
     * Whether to automatically track location when reviewing the timeline.
     */
    val autoTrackForTimelineReview: Boolean = true,
    /**
     * Optional user-selected fallback for devices without OS location services.
     */
    val defaultLocation: DefaultLocation? = null,
) {
    /**
     * Compatibility alias for older callers that still read the pre-experiment name.
     */
    val trackingIntervalMinutes: Long
        get() = minimumPersistIntervalMinutes
}

@Serializable
data class DefaultLocation(
    /**
     * Latitude in decimal degrees.
     */
    val latitude: Double,
    /**
     * Longitude in decimal degrees.
     */
    val longitude: Double,
    /**
     * Altitude value expressed in [altitudeUnit].
     */
    val altitudeValue: Double,
    /**
     * Unit for [altitudeValue].
     */
    val altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
) {
    /**
     * Convert this persisted settings value into the shared location model used by app features.
     */
    fun toLocation(): Location =
        Location(
            latitude = latitude,
            longitude = longitude,
            altitude = LocationAltitude(altitudeValue, altitudeUnit),
        )

    companion object {
        /**
         * Create a persisted default-location setting from a shared location model.
         *
         * @param location Location selected by the user as the fallback for devices without
         *   OS-provided location services.
         */
        fun fromLocation(location: Location): DefaultLocation =
            DefaultLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeValue = location.altitude.value,
                altitudeUnit = location.altitude.units,
            )
    }
}

/**
 * Determines how background location samples are captured and persisted.
 */
@Serializable
enum class LocationCaptureMode {
    /**
     * Default capture mode.
     *
     * The app only records a location when another app has already requested one from the
     * system, adding zero extra battery drain.
     */
    PASSIVE,

    /**
     * Activity-aware capture mode that uses more battery.
     *
     * Runs a foreground service that detects the user's movement (still, walking, driving)
     * and adjusts GPS accuracy and frequency accordingly. High accuracy when moving,
     * low-power heartbeat when stationary.
     */
    ACTIVE,
}
