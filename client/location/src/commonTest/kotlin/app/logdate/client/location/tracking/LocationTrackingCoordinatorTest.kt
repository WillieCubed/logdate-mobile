package app.logdate.client.location.tracking

import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationTrackingExecutionDecisionTest {
    @Test
    fun `disabled background tracking stops everything`() {
        val decision =
            computeLocationTrackingExecutionDecision(
                settings = LocationTrackingSettings(backgroundTrackingEnabled = false),
                canStartDetailedForegroundTracking = false,
            )

        assertTrue(decision.shouldStopScheduledTracking)
        assertTrue(decision.shouldStopOptimizedBackgroundTracking)
        assertTrue(decision.shouldStopDetailedForegroundTracking)
        assertFalse(decision.shouldStartScheduledTracking)
        assertFalse(decision.shouldStartOptimizedBackgroundTracking)
        assertFalse(decision.shouldStartDetailedForegroundTracking)
    }

    @Test
    fun `mirrored mode in background skips detailed start`() {
        val decision =
            computeLocationTrackingExecutionDecision(
                settings =
                    LocationTrackingSettings(
                        backgroundTrackingEnabled = true,
                        captureMode = LocationCaptureMode.EXPERIMENT_MIRRORED,
                    ),
                canStartDetailedForegroundTracking = false,
            )

        assertTrue(decision.shouldStartScheduledTracking)
        assertTrue(decision.shouldStartOptimizedBackgroundTracking)
        assertFalse(decision.shouldStartDetailedForegroundTracking)
        assertFalse(decision.shouldStopDetailedForegroundTracking)
    }

    @Test
    fun `mirrored mode in foreground starts detailed tracking`() {
        val decision =
            computeLocationTrackingExecutionDecision(
                settings =
                    LocationTrackingSettings(
                        backgroundTrackingEnabled = true,
                        captureMode = LocationCaptureMode.EXPERIMENT_MIRRORED,
                    ),
                canStartDetailedForegroundTracking = true,
            )

        assertTrue(decision.shouldStartDetailedForegroundTracking)
        assertFalse(decision.shouldStopDetailedForegroundTracking)
    }

    @Test
    fun `stable mode stops detailed and optimized tracking`() {
        val decision =
            computeLocationTrackingExecutionDecision(
                settings =
                    LocationTrackingSettings(
                        backgroundTrackingEnabled = true,
                        captureMode = LocationCaptureMode.STABLE,
                    ),
                canStartDetailedForegroundTracking = true,
            )

        assertTrue(decision.shouldStartScheduledTracking)
        assertTrue(decision.shouldStopOptimizedBackgroundTracking)
        assertTrue(decision.shouldStopDetailedForegroundTracking)
        assertFalse(decision.shouldStartOptimizedBackgroundTracking)
        assertFalse(decision.shouldStartDetailedForegroundTracking)
    }
}

class ForegroundActivityCounterTest {
    @Test
    fun `first resume transitions to foreground`() {
        val counter = ForegroundActivityCounter()

        assertTrue(counter.onActivityResumed())
        assertTrue(counter.hasForegroundActivities())
    }

    @Test
    fun `second resume stays in foreground without transition`() {
        val counter = ForegroundActivityCounter()
        counter.onActivityResumed()

        assertFalse(counter.onActivityResumed())
        assertTrue(counter.hasForegroundActivities())
    }

    @Test
    fun `pause keeps foreground when another activity is still resumed`() {
        val counter = ForegroundActivityCounter()
        counter.onActivityResumed()
        counter.onActivityResumed()

        counter.onActivityPaused()

        assertTrue(counter.hasForegroundActivities())
    }

    @Test
    fun `pause clamps counter at zero`() {
        val counter = ForegroundActivityCounter()

        counter.onActivityPaused()

        assertFalse(counter.hasForegroundActivities())
    }
}
