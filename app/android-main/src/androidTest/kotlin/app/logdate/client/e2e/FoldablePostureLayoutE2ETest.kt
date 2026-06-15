package app.logdate.client.e2e

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.layout.WindowMetricsCalculator
import app.logdate.client.MainActivity
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_MEMORY_RECALL
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_RECALL_DATE
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_TARGET
import app.logdate.client.testing.onboarding.OnboardingTestFixture
import app.logdate.client.testing.onboarding.putOnboardingTestFixture
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.dsl.module

/**
 * Instrumented E2E coverage for the hinge-aware Home layout.
 *
 * Launches [MainActivity] already onboarded, navigates onto a two-pane-eligible detail (a day
 * timeline) on top of Home, and asserts that the layout responds to posture publishes:
 *
 * - BOOK posture (separating vertical hinge): `LogDateNavDisplay` selects the two-pane
 *   `ListDetailHomeScene`, so the Home (list) pane and the detail pane render simultaneously.
 *   The Home "Create new entry" FAB is therefore visible alongside the day detail's "Close"
 *   affordance — a state that only ever occurs in two-pane mode.
 * - FLAT posture (no separating hinge): the scene falls back to single-pane, so the detail
 *   takes the full screen and the Home FAB is no longer displayed.
 * - TABLETOP posture (separating horizontal hinge): the Home two-pane scene is vertical-hinge
 *   only, so a horizontal hinge must keep Home single-pane (the FAB stays hidden).
 *
 * The FAB / detail-affordance pairing is used as the discriminator because the production
 * `ListDetailHomeScene` panes carry no dedicated test tags (see this suite's reported risks).
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
 * Disabled: discriminating two-pane from single-pane Home at runtime requires a stable marker the
 * production UI does not yet expose — the Home new-entry affordance uses a null content description
 * and `ListDetailHomeScene` panes carry no test tags, so the book-posture assertion has nothing
 * deterministic to wait for. The window-testing posture plumbing this suite exercises is already
 * proven green by [FoldableStateContinuityE2ETest] and [NotificationAttachmentEntryRestorationE2ETest];
 * Home two-pane posture itself is covered by screenshot evidence and the Manual Foldable Evidence
 * Log. Re-enable once the Home list/detail panes expose stable test tags.
 */
@Ignore(
    "Needs a stable test tag on the Home new-entry affordance / ListDetailHomeScene panes to " +
        "discriminate two-pane vs single-pane at runtime; Home posture is covered by screenshots " +
        "and the Manual Foldable Evidence Log.",
)
@RunWith(AndroidJUnit4::class)
class FoldablePostureLayoutE2ETest {
    private val postureSupport = FoldablePostureTestSupport()
    private val koinRule = OnboardingKoinModuleOverrideRule(module {})
    private val activityRule = ActivityScenarioRule<MainActivity>(createDayDetailLaunchIntent())
    private val composeRule = AndroidComposeTestRule(activityRule, ::foldableLayoutActivity)

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(koinRule)
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

        // Two-pane: the Home (list) pane renders its FAB next to the detail pane.
        waitForContentDescription(HOME_NEW_ENTRY_DESCRIPTION)
        composeRule.onNodeWithContentDescription(HOME_NEW_ENTRY_DESCRIPTION).assertIsDisplayed()
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

        // Single-pane: the detail fills the screen and the Home FAB is gone.
        waitForContentDescription(HOME_NEW_ENTRY_DESCRIPTION, shouldExist = false)
        composeRule.onNodeWithContentDescription(DAY_DETAIL_CLOSE_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun tabletopPosture_keepsHomeSinglePane() {
        // The Home two-pane scene is vertical-hinge only. Gating to the narrow phone (< 600dp)
        // removes the width-based two-pane path so the horizontal hinge is the only signal, and
        // it must keep Home single-pane (no FAB).
        assumeTrue(windowWidthDp() < SINGLE_PANE_MAX_WIDTH_DP)

        waitForContentDescription(DAY_DETAIL_CLOSE_DESCRIPTION)

        composeRule.activityRule.scenario.onActivity { activity ->
            postureSupport.publishTabletopPosture(activity)
        }
        composeRule.waitForIdle()

        // A horizontal (tabletop) hinge never produces the vertical two-pane split, so the Home
        // FAB stays hidden while the detail keeps the full screen.
        waitForContentDescription(HOME_NEW_ENTRY_DESCRIPTION, shouldExist = false)
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
        waitForContentDescription(HOME_NEW_ENTRY_DESCRIPTION)

        composeRule.activityRule.scenario.onActivity { _ ->
            postureSupport.publishFlat()
        }
        composeRule.waitForIdle()
        waitForContentDescription(HOME_NEW_ENTRY_DESCRIPTION, shouldExist = false)
    }

    private fun waitForContentDescription(
        description: String,
        shouldExist: Boolean = true,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val exists =
                composeRule
                    .onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            if (shouldExist) exists else !exists
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
        const val HOME_NEW_ENTRY_DESCRIPTION = "Create new entry"
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
