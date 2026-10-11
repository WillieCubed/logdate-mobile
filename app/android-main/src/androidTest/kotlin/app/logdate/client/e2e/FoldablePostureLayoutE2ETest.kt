package app.logdate.client.e2e

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.window.layout.WindowMetricsCalculator
import app.logdate.client.MainActivity
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_MEMORY_RECALL
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_RECALL_DATE
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_TARGET
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.testing.onboarding.OnboardingTestFixture
import app.logdate.client.testing.onboarding.putOnboardingTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.io.File
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Real MainActivity day reading remains independent of Home through posture changes. */
@RunWith(AndroidJUnit4::class)
class FoldablePostureLayoutE2ETest {
    private val postureSupport = FoldablePostureTestSupport()
    private val koinRule = OnboardingKoinModuleOverrideRule(module {})
    private val timelineSeedRule = FoldableTimelineSeedRule()
    private val activityRule = ActivityScenarioRule<MainActivity>(createDayDetailLaunchIntent())
    private val composeRule = AndroidComposeTestRule(activityRule, ::foldableLayoutActivity)

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(koinRule)
            .around(timelineSeedRule)
            .around(postureSupport.publisherRule)
            .around(composeRule)

    @Test
    fun bookKeepsOnlyTheSelectedDayAndItsContentsClearOfTheHinge() {
        waitForDayDetail()
        composeRule.activityRule.scenario.onActivity(postureSupport::publishBookPosture)
        assertReadingActivity("book", verticalHinge = true)
    }

    @Test
    fun flatKeepsOnlyTheSelectedDay() {
        waitForDayDetail()
        composeRule.runOnUiThread { postureSupport.publishFlat() }
        assertReadingActivity("flat")
    }

    @Test
    fun tabletopKeepsTheSelectedMemoryClearOfTheHinge() {
        waitForDayDetail()
        composeRule.activityRule.scenario.onActivity(postureSupport::publishTabletopPosture)
        assertReadingActivity("tabletop", horizontalHinge = true)
    }

    @Test
    fun bookToFlatRetainsTheSelectedMemoryWithoutRestoringHomeChrome() {
        waitForDayDetail()
        composeRule.activityRule.scenario.onActivity(postureSupport::publishBookPosture)
        assertReadingActivity("toggle-book", verticalHinge = true)
        composeRule.runOnUiThread { postureSupport.publishFlat() }
        assertReadingActivity("toggle-flat")
    }

    private fun waitForDayDetail() {
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(dayDetailExitMatcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertReadingActivity(
        name: String,
        verticalHinge: Boolean = false,
        horizontalHinge: Boolean = false,
    ) {
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("workspace_search").assertCountEquals(0)
        composeRule.onAllNodesWithTag("workspace_account").assertCountEquals(0)
        val memories = composeRule.onAllNodesWithText(FOLDABLE_LAYOUT_NOTE_CONTENT)
        memories.assertCountEquals(1)
        memories.onFirst().assertIsDisplayed()
        composeRule.onAllNodes(dayDetailExitMatcher).onFirst().assertIsDisplayed()
        val bounds = memories.fetchSemanticsNodes().single().boundsInWindow
        var windowWidth = 0f
        var windowHeight = 0f
        composeRule.activityRule.scenario.onActivity { activity ->
            val window = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
            windowWidth = window.width().toFloat()
            windowHeight = window.height().toFloat()
        }
        if (verticalHinge) assertTrue(bounds.right <= windowWidth / 2 || bounds.left >= windowWidth / 2)
        if (horizontalHinge) assertTrue(bounds.bottom <= windowHeight / 2 || bounds.top >= windowHeight / 2)
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val output = File(outputDir, "posture-$name.png")
        output.parentFile?.mkdirs()
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(output))
    }

    private val dayDetailExitMatcher = hasContentDescription("Close") or hasContentDescription("Back to your days")
}

private const val FOLDABLE_LAYOUT_RECALL_DATE = "2026-06-15"
private const val FOLDABLE_LAYOUT_NOTE_CONTENT = "Foldable posture layout fixture"
private val FOLDABLE_LAYOUT_NOTE_TIMESTAMP = Instant.parse("2026-06-15T18:00:00Z")

private class FoldableTimelineSeedRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val notesRepository = GlobalContext.get().get<JournalNotesRepository>()
                val noteId = Uuid.random()
                runBlocking {
                    notesRepository.create(
                        JournalNote.Text(
                            uid = noteId,
                            creationTimestamp = FOLDABLE_LAYOUT_NOTE_TIMESTAMP,
                            lastUpdated = FOLDABLE_LAYOUT_NOTE_TIMESTAMP,
                            content = FOLDABLE_LAYOUT_NOTE_CONTENT,
                        ),
                    )
                }
                try {
                    base.evaluate()
                } finally {
                    runBlocking { notesRepository.removeById(noteId) }
                }
            }
        }
}

private fun createDayDetailLaunchIntent(): Intent =
    Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        putOnboardingTestFixture(OnboardingTestFixture.ONBOARDED_HOME)
        putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_MEMORY_RECALL)
        putExtra(EXTRA_AMBIENT_PROMPT_RECALL_DATE, FOLDABLE_LAYOUT_RECALL_DATE)
    }

private fun foldableLayoutActivity(activityRule: ActivityScenarioRule<MainActivity>): MainActivity {
    var activity: MainActivity? = null
    activityRule.scenario.onActivity { launchedActivity ->
        activity = launchedActivity
    }
    return checkNotNull(activity) { "MainActivity was not available from ActivityScenarioRule" }
}
