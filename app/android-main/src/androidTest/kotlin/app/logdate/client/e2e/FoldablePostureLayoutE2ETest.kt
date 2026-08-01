package app.logdate.client.e2e

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Instrumented E2E coverage for the hinge-aware Home layout.
 *
 * Launches [MainActivity] already onboarded, navigates onto a two-pane-eligible detail (a day
 * timeline) on top of Home, and asserts that the layout responds to posture publishes:
 *
 * - BOOK posture (separating vertical hinge): `LogDateNavDisplay` selects the two-pane
 *   `ListDetailHomeScene`, which exposes the stable `home_two_pane_layout` semantics tag while
 *   the day detail's "Close" affordance remains visible.
 * - FLAT posture (no separating hinge): the scene falls back to single-pane, so the detail
 *   takes the full screen and the two-pane tag is absent.
 * - TABLETOP posture (separating horizontal hinge): the Home two-pane scene is vertical-hinge
 *   only, so a horizontal hinge must keep Home single-pane (the tag stays absent).
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
 * - The book→flat toggle needs both behaviors at once — book must split into two ≥320dp panes
 *   yet flat must collapse — which only holds in the medium width band (640dp ≤ width < 840dp).
 *   Neither smoke device sits there, so it skips on both but stays correct for a medium foldable.
 *
 * The production two-pane scene root owns the discriminator tag, so the test does not depend on
 * localized copy, content descriptions, or incidental child controls.
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
    fun bookPosture_rendersBothListAndDetailPanes() {
        // Two-pane needs both panes ≥ 320dp, so the window must be ≥ ~640dp wide (tablet only).
        assumeTrue(windowWidthDp() >= TWO_PANE_MIN_WIDTH_DP)

        // The day-detail "Close" affordance confirms we are on the detail route.
        waitForContentDescription(DAY_DETAIL_CLOSE_DESCRIPTION)

        composeRule.activityRule.scenario.onActivity { activity ->
            postureSupport.publishBookPosture(activity)
        }
        composeRule.waitForIdle()

        waitForTag(HOME_TWO_PANE_LAYOUT_TAG)
        composeRule.onNodeWithTag(HOME_TWO_PANE_LAYOUT_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(DAY_DETAIL_CLOSE_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun flatPosture_collapsesToSinglePane() {
        // A flat posture only collapses to single-pane below the 840dp expanded breakpoint;
        // gating to < 600dp keeps this on the narrow phone where width never forces two-pane.
        assumeTrue(windowWidthDp() < SINGLE_PANE_MAX_WIDTH_DP)

        waitForContentDescription(DAY_DETAIL_CLOSE_DESCRIPTION)

        composeRule.activityRule.scenario.onActivity { _ ->
            postureSupport.publishFlat()
        }
        composeRule.waitForIdle()

        waitForTag(HOME_TWO_PANE_LAYOUT_TAG, shouldExist = false)
        composeRule.onAllNodesWithTag(HOME_TWO_PANE_LAYOUT_TAG).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(DAY_DETAIL_CLOSE_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun tabletopPosture_keepsHomeSinglePane() {
        // The Home two-pane scene is vertical-hinge only. Gating to the narrow phone (< 600dp)
        // removes the width-based two-pane path so the horizontal hinge is the only signal, and
        // it must keep Home single-pane (no two-pane tag).
        assumeTrue(windowWidthDp() < SINGLE_PANE_MAX_WIDTH_DP)

        waitForContentDescription(DAY_DETAIL_CLOSE_DESCRIPTION)

        composeRule.activityRule.scenario.onActivity { activity ->
            postureSupport.publishTabletopPosture(activity)
        }
        composeRule.waitForIdle()

        waitForTag(HOME_TWO_PANE_LAYOUT_TAG, shouldExist = false)
        composeRule.onAllNodesWithTag(HOME_TWO_PANE_LAYOUT_TAG).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(DAY_DETAIL_CLOSE_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun togglingFromBookToFlat_returnsToSinglePane() {
        // Book must split into two ≥ 320dp panes (≥ 640dp wide) while flat must still collapse
        // (< 840dp wide). Only the medium width band satisfies both, so this skips on the phone
        // and the tablet but stays valid for a medium foldable / split-screen window.
        val widthDp = windowWidthDp()
        assumeTrue(widthDp in TWO_PANE_MIN_WIDTH_DP until WIDTH_DP_EXPANDED_LOWER_BOUND)

        waitForContentDescription(DAY_DETAIL_CLOSE_DESCRIPTION)

        composeRule.activityRule.scenario.onActivity { activity ->
            postureSupport.publishBookPosture(activity)
        }
        composeRule.waitForIdle()
        waitForTag(HOME_TWO_PANE_LAYOUT_TAG)
        composeRule.onNodeWithTag(HOME_TWO_PANE_LAYOUT_TAG).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { _ ->
            postureSupport.publishFlat()
        }
        composeRule.waitForIdle()
        waitForTag(HOME_TWO_PANE_LAYOUT_TAG, shouldExist = false)
        composeRule.onAllNodesWithTag(HOME_TWO_PANE_LAYOUT_TAG).assertCountEquals(0)
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

    private fun waitForContentDescription(
        description: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule
                .onAllNodesWithContentDescription(description)
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
        const val DAY_DETAIL_CLOSE_DESCRIPTION = "Close"

        /** Each two-pane column needs ≥ 320dp, so the window must clear ~640dp to split. */
        const val TWO_PANE_MIN_WIDTH_DP = 640

        /** Below the medium breakpoint, width alone never forces a two-pane split. */
        const val SINGLE_PANE_MAX_WIDTH_DP = 600

        /** Material expanded width breakpoint; at/above it, width alone forces two-pane. */
        const val WIDTH_DP_EXPANDED_LOWER_BOUND = 840
    }
}

private const val FOLDABLE_LAYOUT_RECALL_DATE = "2026-06-15"
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
                            content = "Foldable posture layout fixture",
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
