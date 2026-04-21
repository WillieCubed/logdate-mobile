package app.logdate.client.launch

import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Unit tests for the application's launch state machine logic.
 *
 * This suite validates the `reduceLaunchBootstrapState` function, ensuring the app
 * follows the correct transition path from initial startup to a ready state. It
 * tests the interplay between internal initialization stages (like activity creation
 * and UI attachment) and external safety mechanisms like the launch watchdog timer
 * to guarantee a smooth user experience even if some components take longer to load.
 */
class LaunchBootstrapStateTest {
    @Test
    fun `startup remains splash-blocking before watchdog expires`() {
        val snapshot =
            LaunchStageSnapshot()
                .markCompleted(LaunchStage.ActivityCreated)
                .markCompleted(LaunchStage.ComposeAttached)

        val state = reduceLaunchBootstrapState(snapshot)

        assertIs<LaunchBootstrapState.BlockingSplash>(state)
    }

    @Test
    fun `watchdog expiration releases splash while app ui is still unresolved`() {
        val snapshot =
            LaunchStageSnapshot()
                .markCompleted(LaunchStage.ActivityCreated)
                .markCompleted(LaunchStage.ComposeAttached)
                .copy(hasWatchdogExpired = true)

        val state = reduceLaunchBootstrapState(snapshot)

        assertIs<LaunchBootstrapState.SplashReleased>(state)
    }

    @Test
    fun `app ui loaded wins over watchdog expiration`() {
        val snapshot =
            LaunchStageSnapshot()
                .markCompleted(LaunchStage.ActivityCreated)
                .markCompleted(LaunchStage.ComposeAttached)
                .markCompleted(LaunchStage.AppUiLoaded)
                .copy(hasWatchdogExpired = true, hasLoadedAppUi = true)

        val state = reduceLaunchBootstrapState(snapshot)

        assertIs<LaunchBootstrapState.Ready>(state)
    }
}
