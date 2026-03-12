package app.logdate.client.location.tracking

import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings

internal data class LocationTrackingExecutionDecision(
    val shouldStartScheduledTracking: Boolean,
    val shouldStopScheduledTracking: Boolean,
    val shouldStartOptimizedBackgroundTracking: Boolean,
    val shouldStopOptimizedBackgroundTracking: Boolean,
    val shouldStartDetailedForegroundTracking: Boolean,
    val shouldStopDetailedForegroundTracking: Boolean,
)

internal fun computeLocationTrackingExecutionDecision(
    settings: LocationTrackingSettings,
    canStartDetailedForegroundTracking: Boolean,
): LocationTrackingExecutionDecision {
    if (!settings.backgroundTrackingEnabled) {
        return LocationTrackingExecutionDecision(
            shouldStartScheduledTracking = false,
            shouldStopScheduledTracking = true,
            shouldStartOptimizedBackgroundTracking = false,
            shouldStopOptimizedBackgroundTracking = true,
            shouldStartDetailedForegroundTracking = false,
            shouldStopDetailedForegroundTracking = true,
        )
    }

    val mirroredModeEnabled = settings.captureMode == LocationCaptureMode.EXPERIMENT_MIRRORED
    return LocationTrackingExecutionDecision(
        shouldStartScheduledTracking = true,
        shouldStopScheduledTracking = false,
        shouldStartOptimizedBackgroundTracking = mirroredModeEnabled,
        shouldStopOptimizedBackgroundTracking = !mirroredModeEnabled,
        shouldStartDetailedForegroundTracking = mirroredModeEnabled && canStartDetailedForegroundTracking,
        shouldStopDetailedForegroundTracking = !mirroredModeEnabled,
    )
}

internal class ForegroundActivityCounter {
    private var resumedActivityCount = 0

    fun onActivityResumed(): Boolean {
        resumedActivityCount += 1
        return resumedActivityCount == 1
    }

    fun onActivityPaused() {
        resumedActivityCount = (resumedActivityCount - 1).coerceAtLeast(0)
    }

    fun hasForegroundActivities(): Boolean = resumedActivityCount > 0

    fun reset() {
        resumedActivityCount = 0
    }
}
