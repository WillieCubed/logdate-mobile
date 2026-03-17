package app.logdate.client.location.tracking

import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings

internal data class LocationTrackingExecutionDecision(
    val shouldStartScheduledTracking: Boolean,
    val shouldStopScheduledTracking: Boolean,
    val shouldStartOptimizedBackgroundTracking: Boolean,
    val shouldStopOptimizedBackgroundTracking: Boolean,
    val shouldStartActivityAwareTracking: Boolean,
    val shouldStopActivityAwareTracking: Boolean,
)

internal fun computeLocationTrackingExecutionDecision(settings: LocationTrackingSettings): LocationTrackingExecutionDecision {
    if (!settings.backgroundTrackingEnabled) {
        return LocationTrackingExecutionDecision(
            shouldStartScheduledTracking = false,
            shouldStopScheduledTracking = true,
            shouldStartOptimizedBackgroundTracking = false,
            shouldStopOptimizedBackgroundTracking = true,
            shouldStartActivityAwareTracking = false,
            shouldStopActivityAwareTracking = true,
        )
    }

    val activeMode = settings.captureMode == LocationCaptureMode.ACTIVE
    return LocationTrackingExecutionDecision(
        shouldStartScheduledTracking = true,
        shouldStopScheduledTracking = false,
        shouldStartOptimizedBackgroundTracking = activeMode,
        shouldStopOptimizedBackgroundTracking = !activeMode,
        shouldStartActivityAwareTracking = activeMode,
        shouldStopActivityAwareTracking = !activeMode,
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
