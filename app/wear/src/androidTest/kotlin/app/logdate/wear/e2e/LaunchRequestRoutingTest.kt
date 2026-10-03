package app.logdate.wear.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent

/**
 * A launch that arrives while the watch app is open, as when a tile is tapped, goes to the open
 * activity and opens the requested screen there.
 */
@RunWith(AndroidJUnit4::class)
class LaunchRequestRoutingTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val onboarding = context.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun openTheCurrentWatchApp() {
        onboarding.edit().putBoolean(ONBOARDING_COMPLETE, true).commit()
        runBlocking {
            KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false)
        }
    }

    @After
    fun tearDown() {
        // onNewIntent replaces the activity's intent, so ActivityScenario no longer matches the activity
        // to its launch and close() would wait for a DESTROYED it never sees.
        scenario?.onActivity { it.finish() }
        onboarding.edit().remove(ONBOARDING_COMPLETE).commit()
    }

    @Test
    fun a_request_that_arrives_while_the_app_is_open_opens_its_screen_in_the_same_activity() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithContentDescription(RECORD_AUDIO) }

        launched.onActivity { activity ->
            activity.startActivity(
                Intent(activity, MainActivity::class.java).putExtra(WearLaunchRequest.EXTRA_ROUTE, "mood"),
            )
        }

        waitFor { composeRule.onAllNodesWithText(MOOD_TITLE) }
        launched.onActivity { activity ->
            assertEquals(
                "The open activity should receive the request instead of a second copy",
                "mood",
                activity.intent.getStringExtra(WearLaunchRequest.EXTRA_ROUTE),
            )
        }
    }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        // WearOnboardingScreen's preferences: the current app shows onboarding until this is set.
        const val ONBOARDING_PREFERENCES = "wear_onboarding"
        const val ONBOARDING_COMPLETE = "onboarding_complete"

        const val RECORD_AUDIO = "Record Audio"
        const val MOOD_TITLE = "How are you feeling?"
        const val TIMEOUT_MS = 10_000L
    }
}
