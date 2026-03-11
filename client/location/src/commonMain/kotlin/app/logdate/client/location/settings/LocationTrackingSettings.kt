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
    val captureMode: LocationCaptureMode = LocationCaptureMode.STABLE,
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

@Serializable
enum class LocationCaptureMode {
    STABLE,
    EXPERIMENT_MIRRORED,
}
