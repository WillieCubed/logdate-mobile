package app.logdate.client.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.EditorActivity
import kotlin.test.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Instrumented E2E coverage that multi-window editor windows stay hinge-aware and keep
 * independent state across a posture publish.
 *
 * Extends the multi-window identity guarantees in `MultiWindowEditorE2ETest`: two
 * [EditorActivity] instances are launched as distinct new-entry windows, the primary window
 * receives typed text and a separating book hinge, and the suite asserts the primary window
 * keeps its draft while the secondary window is a genuinely independent instance — never the
 * same instance re-presenting the primary's draft.
 *
 * Both windows launch as new-entry editors (no `entry_id`) so the text input renders
 * deterministically on an emulator without needing a seeded entry in the repository. The
 * secondary's independence is asserted through distinct runtime identity (a different activity
 * instance in a different task) rather than a rendered-UI comparison: standing up a second live
 * Compose test environment alongside the primary `composeRule` is unreliable because the two
 * would share the global Compose idling/clock state, so this suite verifies instance separation
 * directly and leaves the primary's surviving draft as the non-interference signal.
 */
@RunWith(AndroidJUnit4::class)
class FoldableMultiInstanceEditorE2ETest {
    private val postureSupport = FoldablePostureTestSupport()
    private val primaryActivityRule =
        ActivityScenarioRule<EditorActivity>(createNewEntryWindowIntent(PRIMARY_SEED_TEXT))
    private val composeRule = AndroidComposeTestRule(primaryActivityRule, ::editorInstanceActivity)

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(postureSupport.publisherRule)
            .around(composeRule)

    @Test
    fun primaryEditorKeepsTextWhenPublishingBookPosture() {
        appendToPrimaryEditor(PRIMARY_APPENDED_TEXT)

        composeRule.activityRule.scenario.onActivity { activity ->
            postureSupport.publishBookPosture(activity)
        }
        composeRule.waitForIdle()

        waitForTag(EDITOR_TEXT_INPUT_TAG)
        composeRule.onNodeWithTag(EDITOR_TEXT_INPUT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(EDITOR_TEXT_INPUT_TAG).assertTextContains(PRIMARY_APPENDED_TEXT)
    }

    @Test
    fun secondaryEditorInstanceIsIndependentOfPrimary() {
        appendToPrimaryEditor(PRIMARY_APPENDED_TEXT)

        composeRule.activityRule.scenario.onActivity { activity ->
            postureSupport.publishBookPosture(activity)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(EDITOR_TEXT_INPUT_TAG).assertTextContains(PRIMARY_APPENDED_TEXT)

        // Capture the primary window's runtime identity to compare against the secondary.
        var primaryInstanceId = 0
        var primaryTaskId = 0
        composeRule.activityRule.scenario.onActivity { activity ->
            primaryInstanceId = System.identityHashCode(activity)
            primaryTaskId = activity.taskId
        }

        // Launch a second, distinctly-seeded editor window. It must be its own running instance
        // in its own task, not the primary activity re-presenting the same draft.
        val secondaryIntent = createNewEntryWindowIntent(SECONDARY_SEED_TEXT)
        ActivityScenario.launch<EditorActivity>(secondaryIntent).use { secondary ->
            secondary.moveToState(Lifecycle.State.RESUMED)
            secondary.onActivity { activity ->
                assertNotEquals(
                    primaryInstanceId,
                    System.identityHashCode(activity),
                    "Secondary editor must be a distinct activity instance, not the primary",
                )
                assertNotEquals(
                    primaryTaskId,
                    activity.taskId,
                    "Secondary editor must run in its own task, isolated from the primary",
                )
            }
        }

        // The primary window is untouched by the secondary's lifecycle and still hinge-aware.
        waitForTag(EDITOR_TEXT_INPUT_TAG)
        composeRule.onNodeWithTag(EDITOR_TEXT_INPUT_TAG).assertTextContains(PRIMARY_APPENDED_TEXT)
    }

    private fun appendToPrimaryEditor(text: String) {
        waitForTag(EDITOR_TEXT_INPUT_TAG)
        composeRule.onNodeWithTag(EDITOR_TEXT_INPUT_TAG).performTextInput(text)
        composeRule.waitForIdle()
    }

    private fun waitForTag(
        tag: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val EDITOR_TEXT_INPUT_TAG = "editor_text_input"
        const val PRIMARY_SEED_TEXT = "Primary window seed"
        const val SECONDARY_SEED_TEXT = "Secondary window seed"
        const val PRIMARY_APPENDED_TEXT = "Primary appended draft"
    }
}

private fun createNewEntryWindowIntent(initialText: String): Intent {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return EditorActivity
        .createIntent(context, initialText = initialText)
        // Clear the multi-task launch flags so ActivityScenario can own each instance directly;
        // the manifest's documentLaunchMode="always" still places each launch in its own task.
        .apply {
            flags = 0
            setClass(ApplicationProvider.getApplicationContext<Context>(), EditorActivity::class.java)
        }
}

private fun editorInstanceActivity(activityRule: ActivityScenarioRule<EditorActivity>): EditorActivity {
    var activity: EditorActivity? = null
    activityRule.scenario.onActivity { launchedActivity ->
        activity = launchedActivity
    }
    return checkNotNull(activity) { "EditorActivity was not available from ActivityScenarioRule" }
}
