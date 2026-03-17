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
            )

        assertTrue(decision.shouldStopScheduledTracking)
        assertTrue(decision.shouldStopOptimizedBackgroundTracking)
        assertTrue(decision.shouldStopActivityAwareTracking)
        assertFalse(decision.shouldStartScheduledTracking)
        assertFalse(decision.shouldStartOptimizedBackgroundTracking)
        assertFalse(decision.shouldStartActivityAwareTracking)
    }

    @Test
    fun `active mode starts all three tracking channels`() {
        val decision =
            computeLocationTrackingExecutionDecision(
                settings =
                    LocationTrackingSettings(
                        backgroundTrackingEnabled = true,
                        captureMode = LocationCaptureMode.ACTIVE,
                    ),
            )

        assertTrue(decision.shouldStartScheduledTracking)
        assertTrue(decision.shouldStartOptimizedBackgroundTracking)
        assertTrue(decision.shouldStartActivityAwareTracking)
        assertFalse(decision.shouldStopScheduledTracking)
        assertFalse(decision.shouldStopOptimizedBackgroundTracking)
        assertFalse(decision.shouldStopActivityAwareTracking)
    }

    @Test
    fun `passive mode stops activity-aware and optimized tracking`() {
        val decision =
            computeLocationTrackingExecutionDecision(
                settings =
                    LocationTrackingSettings(
                        backgroundTrackingEnabled = true,
                        captureMode = LocationCaptureMode.PASSIVE,
                    ),
            )

        assertTrue(decision.shouldStartScheduledTracking)
        assertTrue(decision.shouldStopOptimizedBackgroundTracking)
        assertTrue(decision.shouldStopActivityAwareTracking)
        assertFalse(decision.shouldStartOptimizedBackgroundTracking)
        assertFalse(decision.shouldStartActivityAwareTracking)
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
