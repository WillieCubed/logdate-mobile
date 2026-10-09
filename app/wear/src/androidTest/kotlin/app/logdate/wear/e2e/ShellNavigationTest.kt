package app.logdate.wear.e2e

import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.v2.WearAppV2Tags
import app.logdate.wear.v2.today.TodayTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent

/**
 * Launches the real activity with WATCH_EXPERIENCE_V2 on and checks the new app's shell: it opens
 * on Today, a launch request that arrives while it is open lands on a single Today, and a recreated
 * activity comes back to Today.
 */
@RunWith(AndroidJUnit4::class)
class ShellNavigationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun openTheNewWatchApp() {
        setNewWatchApp(enabled = true)
    }

    @After
    fun tearDown() {
        // onNewIntent replaces the activity's intent, so ActivityScenario no longer matches the activity
        // to its launch and close() would wait for a DESTROYED it never sees.
        scenario?.onActivity { it.finish() }
        setNewWatchApp(enabled = false)
    }

    @Test
    fun the_new_watch_app_opens_on_today() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }
        composeRule.onNodeWithTag(WearAppV2Tags.SHELL).assertIsDisplayed()
        composeRule.onNodeWithTag(TodayTags.PAGE).assertIsDisplayed()
    }

    @Test
    fun a_request_that_arrives_while_open_lands_on_a_single_today() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }

        launched.onActivity { activity ->
            activity.startActivity(Intent(activity, MainActivity::class.java).putExtra(WearLaunchRequest.EXTRA_ROUTE, ROUTE))
        }
        composeRule.waitUntil(TIMEOUT_MS) { deliveredRoute(launched) == ROUTE }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(TodayTags.PAGE).assertCountEquals(1)
        composeRule.onNodeWithTag(TodayTags.PAGE).assertIsDisplayed()
    }

    @Test
    fun a_recreated_activity_comes_back_to_today() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }

        launched.recreate()

        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }
        composeRule.onNodeWithTag(TodayTags.PAGE).assertIsDisplayed()
    }

    private fun deliveredRoute(scenario: ActivityScenario<MainActivity>): String? {
        var route: String? = null
        scenario.onActivity { activity -> route = activity.intent.getStringExtra(WearLaunchRequest.EXTRA_ROUTE) }
        return route
    }

    private fun setNewWatchApp(enabled: Boolean) =
        runBlocking {
            KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val ROUTE = "memories"
        const val TIMEOUT_MS = 10_000L
    }
}
