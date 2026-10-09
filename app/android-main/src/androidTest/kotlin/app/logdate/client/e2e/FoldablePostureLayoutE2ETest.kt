package app.logdate.client.e2e

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assume.assumeTrue
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

/**
 * Instrumented E2E coverage for the hinge-aware Home layout.
 *
 * Launches [MainActivity] already onboarded, navigates onto a two-pane-eligible detail (a day
 * timeline) on top of Home, and asserts that the layout responds to posture publishes:
 *
 * - BOOK posture (separating vertical hinge): `LogDateNavDisplay` selects the two-pane
 *   `ListDetailHomeScene`. The legacy presentation exposes `home_two_pane_layout`; the workspace
 *   presentation displays the seeded memory in separate browse and detail panels.
 * - FLAT posture (no separating hinge): the scene falls back to single-pane, so the detail
 *   takes the full screen and the two-pane tag is absent.
 * - TABLETOP posture (separating horizontal hinge): the legacy Home scene remains single-pane.
 *   The workspace may show browse and detail above and below the hinge, without overlap.
 *
 * This suite runs on the `smokeDevices` group (a ~411dp phone and a ~1280dp tablet), but the
 * production two-pane gate is width-sensitive, so each test self-selects the devices it can pass
 * on via [assumeTrue]:
 *
 * - The two-pane (book) assertions need each pane to clear the 320dp minimum, i.e. a window at
 *   least ~640dp wide, so they only run on the wide tablet.
 * - The flat / tabletop collapse-to-single-pane assertions need width alone to *not* force a
 *   two-pane split (which happens at the 840dp expanded breakpoint). They run on the narrow
 *   phone (< 600dp), where width never triggers two-pane and the posture is the only signal.
 * - The book→flat toggle runs in the medium width band (640dp ≤ width < 840dp). The legacy
 *   scene collapses there; the workspace retains two panels when they still fit after the hinge
 *   clears. In both presentations the selected memory must remain visible.
 *
 * The workspace assertion checks both populated panels and their non-overlapping geometry.
 */
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
    fun `book posture renders both list and detail panes`() {
        // Two-pane needs both panes ≥ 320dp, so the window must be ≥ ~640dp wide (tablet only).
        assumeTrue(windowWidthDp() >= TWO_PANE_MIN_WIDTH_DP)

        // The day-detail return affordance confirms we are on the detail route.
        waitForDayDetailExit()

        composeRule.activityRule.scenario.onActivity { activity ->
            postureSupport.publishBookPosture(activity)
        }
        composeRule.waitForIdle()

        assertBookPanelsDisplayed()
        composeRule.onAllNodes(dayDetailExitMatcher).onFirst().assertIsDisplayed()
        capturePosture("book")
    }

    @Test
    fun `flat posture collapses to single pane`() {
        // A flat posture only collapses to single-pane below the 840dp expanded breakpoint;
        // gating to < 600dp keeps this on the narrow phone where width never forces two-pane.
        assumeTrue(windowWidthDp() < SINGLE_PANE_MAX_WIDTH_DP)

        waitForDayDetailExit()

        composeRule.activityRule.scenario.onActivity { _ ->
            postureSupport.publishFlat()
        }
        composeRule.waitForIdle()

        waitForTag(HOME_TWO_PANE_LAYOUT_TAG, shouldExist = false)
        composeRule.onAllNodesWithTag(HOME_TWO_PANE_LAYOUT_TAG).assertCountEquals(0)
        assertSingleMemoryDisplayed()
        composeRule.onAllNodes(dayDetailExitMatcher).onFirst().assertIsDisplayed()
        capturePosture("flat")
    }

    @Test
    fun `tabletop posture keeps populated panels clear of hinge`() {
        // The Home two-pane scene is vertical-hinge only. Gating to the narrow phone (< 600dp)
        // removes the width-based two-pane path so the horizontal hinge is the only signal, and
        // the legacy side-by-side scene must remain absent.
        assumeTrue(windowWidthDp() < SINGLE_PANE_MAX_WIDTH_DP)

        waitForDayDetailExit()
        val workspace = isWorkspacePresentation()

        composeRule.activityRule.scenario.onActivity { activity ->
            postureSupport.publishTabletopPosture(activity)
        }
        composeRule.waitForIdle()

        waitForTag(HOME_TWO_PANE_LAYOUT_TAG, shouldExist = false)
        composeRule.onAllNodesWithTag(HOME_TWO_PANE_LAYOUT_TAG).assertCountEquals(0)
        assertTabletopPanelsDisplayed(workspace)
        assertDayDetailExit(workspace)
        capturePosture("tabletop")
    }

    @Test
    fun `toggling from book to flat retains the selected memory`() {
        val widthDp = windowWidthDp()
        assumeTrue(widthDp in TWO_PANE_MIN_WIDTH_DP until WIDTH_DP_EXPANDED_LOWER_BOUND)

        waitForDayDetailExit()
        val workspace = isWorkspacePresentation()

        composeRule.activityRule.scenario.onActivity { activity ->
            postureSupport.publishBookPosture(activity)
        }
        composeRule.waitForIdle()
        assertBookPanelsDisplayed()
        capturePosture("toggle-book")

        composeRule.activityRule.scenario.onActivity { _ ->
            postureSupport.publishFlat()
        }
        composeRule.waitForIdle()
        waitForTag(HOME_TWO_PANE_LAYOUT_TAG, shouldExist = false)
        composeRule.onAllNodesWithTag(HOME_TWO_PANE_LAYOUT_TAG).assertCountEquals(0)
        assertDayDetailExit(workspace)
        if (workspace) {
            assertBookPanelsDisplayed()
        } else {
            assertSingleMemoryDisplayed()
        }
        capturePosture("toggle-flat")
    }

    private fun waitForTag(
        tag: String,
        shouldExist: Boolean = true,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val exists =
                composeRule
                    .onAllNodesWithTag(tag)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            if (shouldExist) exists else !exists
        }
    }

    private fun assertBookPanelsDisplayed() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(HOME_TWO_PANE_LAYOUT_TAG).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(FOLDABLE_LAYOUT_NOTE_CONTENT).fetchSemanticsNodes().size == 2
        }
        if (composeRule.onAllNodesWithTag(HOME_TWO_PANE_LAYOUT_TAG).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(HOME_TWO_PANE_LAYOUT_TAG).assertIsDisplayed()
        } else {
            val memories = composeRule.onAllNodesWithText(FOLDABLE_LAYOUT_NOTE_CONTENT)
            memories.assertCountEquals(2)
            memories[0].assertIsDisplayed()
            memories[1].assertIsDisplayed()
            val bounds = memories.fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
            assertTrue(bounds[0].right <= bounds[1].left, "Browse and detail memories must occupy separate panels")
        }
    }

    private fun assertSingleMemoryDisplayed() {
        val memories = composeRule.onAllNodesWithText(FOLDABLE_LAYOUT_NOTE_CONTENT)
        memories.assertCountEquals(1)
        memories.onFirst().assertIsDisplayed()
    }

    private fun assertTabletopPanelsDisplayed(workspace: Boolean) {
        val memories = composeRule.onAllNodesWithText(FOLDABLE_LAYOUT_NOTE_CONTENT)
        if (!workspace) {
            assertSingleMemoryDisplayed()
        } else {
            memories.assertCountEquals(2)
            memories[0].assertIsDisplayed()
            memories[1].assertIsDisplayed()
            val ancestors = memories.fetchSemanticsNodes().map { node -> generateSequence(node) { it.parent }.toList() }
            val sharedIds = ancestors[0].map { it.id }.intersect(ancestors[1].map { it.id }.toSet())
            val bounds = ancestors.map { chain -> chain.takeWhile { it.id !in sharedIds }.last().boundsInWindow }.sortedBy { it.top }
            assertTrue(bounds[0].bottom <= bounds[1].top, "Tabletop memories must occupy separate vertical panels")
            assertTrue(
                maxOf(bounds[0].left, bounds[1].left) < minOf(bounds[0].right, bounds[1].right),
                "Tabletop panels must stack above and below the hinge rather than side by side",
            )
            var hingeY = 0f
            composeRule.activityRule.scenario.onActivity { activity ->
                hingeY = WindowMetricsCalculator
                    .getOrCreate()
                    .computeCurrentWindowMetrics(activity)
                    .bounds
                    .height() / 2f
            }
            assertTrue(bounds[0].bottom <= hingeY && bounds[1].top >= hingeY, "Entire panels must remain clear of the hinge")
        }
    }

    private fun isWorkspacePresentation() =
        composeRule.onAllNodes(hasContentDescription("Back to your days")).fetchSemanticsNodes().isNotEmpty()

    private fun assertDayDetailExit(workspace: Boolean) {
        val description = if (workspace) "Back to your days" else "Close"
        composeRule.onAllNodes(hasContentDescription(description)).onFirst().assertIsDisplayed()
    }

    private fun capturePosture(name: String) {
        composeRule.waitForIdle()
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val output = File(outputDir, "posture-$name.png")
        output.parentFile?.mkdirs()
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(output))
    }

    private val dayDetailExitMatcher =
        hasContentDescription("Close") or hasContentDescription("Back to your days")

    private fun waitForDayDetailExit(timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule
                .onAllNodes(dayDetailExitMatcher)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /**
     * Reads the current window width in dp from inside the activity. Uses
     * [WindowMetricsCalculator] (the same source the production hinge-aware layout consults) so
     * the gate matches the breakpoints the two-pane scene actually evaluates.
     */
    private fun windowWidthDp(): Int {
        var widthDp = 0
        composeRule.activityRule.scenario.onActivity { activity ->
            val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
            widthDp = (bounds.width() / activity.resources.displayMetrics.density).toInt()
        }
        return widthDp
    }

    private companion object {
        const val HOME_TWO_PANE_LAYOUT_TAG = "home_two_pane_layout"

        /** Each two-pane column needs ≥ 320dp, so the window must clear ~640dp to split. */
        const val TWO_PANE_MIN_WIDTH_DP = 640

        /** Below the medium breakpoint, width alone never forces a two-pane split. */
        const val SINGLE_PANE_MAX_WIDTH_DP = 600

        /** Material expanded width breakpoint; at/above it, width alone forces two-pane. */
        const val WIDTH_DP_EXPANDED_LOWER_BOUND = 840
    }
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
