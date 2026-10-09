package app.logdate.wear.e2e

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.v2.WearAppV2Tags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent

/**
 * Launches the real activity with WATCH_EXPERIENCE_V2 off and on. The flag picks the watch app
 * once, when the activity is created, and a recreated activity shows the app it had.
 */
@RunWith(AndroidJUnit4::class)
class ExperienceGateTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val onboarding = context.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun skipOnboarding() {
        onboarding.edit().putBoolean(ONBOARDING_COMPLETE, true).commit()
    }

    @After
    fun tearDown() {
        scenario?.close()
        setNewWatchApp(enabled = false)
        onboarding.edit().remove(ONBOARDING_COMPLETE).commit()
    }

    @Test
    fun the_current_watch_app_opens_while_the_flag_is_off() {
        setNewWatchApp(enabled = false)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        waitFor { composeRule.onAllNodesWithContentDescription(RECORD_AUDIO) }
        composeRule.onNodeWithContentDescription(RECORD_AUDIO).assertIsDisplayed()
        composeRule.onAllNodesWithTag(WearAppV2Tags.SHELL).assertCountEquals(0)
    }

    @Test
    fun the_new_watch_app_opens_while_the_flag_is_on() {
        setNewWatchApp(enabled = true)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        waitFor { composeRule.onAllNodesWithTag(WearAppV2Tags.SHELL) }
        composeRule.onNodeWithTag(WearAppV2Tags.SHELL).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(RECORD_AUDIO).assertCountEquals(0)
    }

    @Test
    fun turning_the_flag_off_does_not_swap_an_open_activity() {
        setNewWatchApp(enabled = true)
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithTag(WearAppV2Tags.SHELL) }

        setNewWatchApp(enabled = false)
        launched.recreate()

        waitFor { composeRule.onAllNodesWithTag(WearAppV2Tags.SHELL) }
        composeRule.onAllNodesWithContentDescription(RECORD_AUDIO).assertCountEquals(0)
    }

    private fun setNewWatchApp(enabled: Boolean) =
        runBlocking {
            KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        // WearOnboardingScreen's preferences: the current app shows onboarding until this is set.
        const val ONBOARDING_PREFERENCES = "wear_onboarding"
        const val ONBOARDING_COMPLETE = "onboarding_complete"

        // The record button's content description on the current Home screen.
        const val RECORD_AUDIO = "Record Audio"
        const val TIMEOUT_MS = 10_000L
    }
}
