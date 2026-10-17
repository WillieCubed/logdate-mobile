package app.logdate.wear.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.v2.WearAppV2Tags
import app.logdate.wear.v2.flow.FlowTags
import app.logdate.wear.v2.moments.MomentTags
import app.logdate.wear.v2.settings.WatchSettingsScreenTags
import app.logdate.wear.v2.today.TodayTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.time.Clock

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

    @Test
    fun moments_and_settings_open_as_separate_screens() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Moments other tests saved can push the button below the composed items, so scroll to it.
        waitFor { composeRule.onAllNodesWithTag(TodayTags.SUMMARY) }

        composeRule.onNodeWithTag(TodayTags.PAGE).performScrollToNode(hasTestTag(TodayTags.MOMENTS))
        composeRule.onNodeWithTag(TodayTags.MOMENTS).performClick()
        waitFor { composeRule.onAllNodesWithTag(FlowTags.LIST) }

        composeRule.onNodeWithTag(FlowTags.LIST).performScrollToNode(hasTestTag(FlowTags.SETTINGS))
        composeRule.onNodeWithTag(FlowTags.SETTINGS).performClick()
        waitFor { composeRule.onAllNodesWithTag(WatchSettingsScreenTags.LIST) }
        composeRule.onNodeWithTag(WatchSettingsScreenTags.LIST).assertIsDisplayed()
    }

    @Test
    fun a_moment_link_opens_that_moment() {
        val now = Clock.System.now()
        val note = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "A moment opened from a link")
        val notes = KoinJavaComponent.getKoin().get<JournalNotesRepository>()
        runBlocking { notes.create(note) }
        try {
            val link =
                Intent(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)
                    .putExtra(WearLaunchRequest.EXTRA_ROUTE, "moment")
                    .putExtra(WearLaunchRequest.EXTRA_NOTE_ID, note.uid.toString())
            scenario = ActivityScenario.launch(link)

            waitFor { composeRule.onAllNodesWithTag(MomentTags.DETAILS) }
            composeRule.onNodeWithText("A moment opened from a link").assertIsDisplayed()
        } finally {
            runBlocking { notes.removeById(note.uid) }
        }
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

    /** Waits for [nodes]; a timeout reports what the screen showed instead, so a failure says where the app was. */
    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        try {
            composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError(
                "The screen showed instead:\n${composeRule.onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE)}",
                timeout,
            )
        }
    }

    private companion object {
        /** A request whose screen is Today itself, so a second Today would show if the shell stacked it. */
        const val ROUTE = "mood"
        const val TIMEOUT_MS = 10_000L
    }
}
