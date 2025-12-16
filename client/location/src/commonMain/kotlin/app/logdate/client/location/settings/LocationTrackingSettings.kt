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
     * Interval between location updates in minutes.
     * Minimum value is 15 minutes due to Android WorkManager constraints.
     */
    val trackingIntervalMinutes: Long = 30,
    
    /**
     * Whether to automatically track location when creating journal entries.
     */
    val autoTrackForJournalEntries: Boolean = true,
    
    /**
     * Whether to automatically track location when reviewing the timeline.
     */
    val autoTrackForTimelineReview: Boolean = true,

    /**
     * Whether to show the location timeline in the UI.
     */
    val showLocationTimeline: Boolean? = true
)