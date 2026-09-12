package app.logdate.client.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [LaunchStageTracker], the mutable holder MainActivity uses to record launch
 * milestones and decide when the splash screen may be released.
 */
class LaunchStageTrackerTest {
    @Test
    fun `marking a later stage advances the snapshot and reports the change`() {
        val tracker = LaunchStageTracker()

        assertTrue(tracker.mark(LaunchStage.ActivityCreated))

        assertEquals(LaunchStage.ActivityCreated, tracker.snapshot.latestCompletedStage)
    }

    @Test
    fun `marking the same stage twice reports no change`() {
        val tracker = LaunchStageTracker()
        tracker.mark(LaunchStage.ActivityCreated)

        assertFalse(tracker.mark(LaunchStage.ActivityCreated))
    }

    @Test
    fun `marking an earlier stage after a later one keeps the later stage`() {
        val tracker = LaunchStageTracker()
        tracker.mark(LaunchStage.ComposeAttached)

        assertFalse(tracker.mark(LaunchStage.ActivityCreated))

        assertEquals(LaunchStage.ComposeAttached, tracker.snapshot.latestCompletedStage)
    }

    @Test
    fun `splash blocks until the watchdog expires`() {
        val tracker = LaunchStageTracker()
        tracker.mark(LaunchStage.ComposeAttached)
        assertIs<LaunchBootstrapState.BlockingSplash>(tracker.bootstrapState)

        tracker.expireWatchdog()

        assertIs<LaunchBootstrapState.SplashReleased>(tracker.bootstrapState)
        assertTrue(tracker.snapshot.hasWatchdogExpired)
    }

    @Test
    fun `app ui loaded makes the launch ready even after the watchdog expired`() {
        val tracker = LaunchStageTracker()
        tracker.expireWatchdog()

        assertTrue(tracker.mark(LaunchStage.AppUiLoaded))

        assertIs<LaunchBootstrapState.Ready>(tracker.bootstrapState)
    }
}
