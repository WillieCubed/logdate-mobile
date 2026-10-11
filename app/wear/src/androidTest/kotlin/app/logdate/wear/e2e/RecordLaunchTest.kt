package app.logdate.wear.e2e

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.wear.launch.WearLaunchIntents
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.recording.WearRecorder
import app.logdate.wear.v2.recorder.RecorderTags
import app.logdate.wear.v2.today.TodayTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertFalse

/**
 * Launches the new watch app with the Record request, the way the Record complication, the Today
 * tile and a reminder's Record action do, on a Wear OS emulator with the microphone allowed.
 */
@RunWith(AndroidJUnit4::class)
class RecordLaunchTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val recorder: WearRecorder get() = KoinJavaComponent.getKoin().get()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS).forEach { permission ->
            automation.grantRuntimePermission(context.packageName, permission)
        }
        setNewWatchApp(enabled = true)
    }

    @After
    fun tearDown() {
        if (recorder.isRecording.value) runBlocking { recorder.discard() }
        // onNewIntent replaces the activity's intent, so ActivityScenario.close() could wait forever.
        scenario?.onActivity { it.finish() }
        setNewWatchApp(enabled = false)
    }

    @Test
    fun a_record_launch_starts_a_recording() {
        scenario = ActivityScenario.launch(recordIntent())

        composeRule.waitUntil(TIMEOUT_MS) { recorder.isRecording.value }
        waitFor { composeRule.onAllNodesWithTag(RecorderTags.ELAPSED) }
    }

    @Test
    fun a_record_request_while_open_starts_a_recording() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }

        launched.onActivity { activity -> activity.startActivity(recordIntent()) }

        composeRule.waitUntil(TIMEOUT_MS) { recorder.isRecording.value }
    }

    @Test
    fun a_recreated_activity_does_not_record_again() {
        val launched = ActivityScenario.launch<MainActivity>(recordIntent()).also { scenario = it }
        composeRule.waitUntil(TIMEOUT_MS) { recorder.isRecording.value }
        waitFor { composeRule.onAllNodesWithTag(RecorderTags.DISCARD) }
        // Discard asks once, then discards on the second tap.
        repeat(2) { composeRule.onNodeWithTag(RecorderTags.DISCARD).performClick() }
        composeRule.waitUntil(TIMEOUT_MS) { !recorder.isRecording.value }

        launched.recreate()
        waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }
        SystemClock.sleep(SETTLE_MS)

        assertFalse(recorder.isRecording.value)
    }

    private fun recordIntent(): Intent =
        Intent(context, MainActivity::class.java).putExtra(WearLaunchRequest.EXTRA_ROUTE, WearLaunchIntents.ROUTE_RECORD)

    private fun setNewWatchApp(enabled: Boolean) =
        runBlocking {
            KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 2_000L
    }
}
