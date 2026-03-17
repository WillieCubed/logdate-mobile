package app.logdate.client.location.settings

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
) {
    /**
     * Compatibility alias for older callers that still read the pre-experiment name.
     */
    val trackingIntervalMinutes: Long
        get() = minimumPersistIntervalMinutes
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
