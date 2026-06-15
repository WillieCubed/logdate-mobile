package app.logdate.client.e2e

import android.app.Activity
import androidx.window.layout.FoldingFeature.Orientation
import androidx.window.layout.FoldingFeature.State
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule

/**
 * Shared helpers for driving foldable postures in instrumented tests.
 *
 * The production foldable detection (`rememberFoldableLayoutInfo`) collects from
 * `WindowInfoTracker.windowLayoutInfo(context)`. [WindowLayoutInfoPublisherRule] hijacks that
 * stream so a test can publish a synthetic [WindowLayoutInfo] containing a half-opened
 * [androidx.window.layout.FoldingFeature]. The test `FoldingFeature(...)` factory resolves the
 * hinge bounds against the supplied activity's window via `WindowMetricsCalculator`, which is
 * why every posture publisher takes the activity under test.
 *
 * This is intentionally a plain helper (not a JUnit test class) so each foldable suite can wrap
 * it in its own [org.junit.rules.RuleChain] alongside the Koin override and Compose rules — the
 * same RuleChain idiom used by `MainActivityRecreationE2ETest` and `IncomingShareE2ETest`.
 */
class FoldablePostureTestSupport(
    val publisherRule: WindowLayoutInfoPublisherRule = WindowLayoutInfoPublisherRule(),
) {
    /**
     * Publishes a BOOK posture: a half-opened, separating VERTICAL hinge that splits the window
     * into a left and right pane. This is the posture that should activate the hinge-aware
     * two-pane (`FoldableBookLayout` / `ListDetailHomeScene` vertical) layouts.
     */
    fun publishBookPosture(activity: Activity) {
        val feature =
            FoldingFeature(
                activity = activity,
                state = State.HALF_OPENED,
                orientation = Orientation.VERTICAL,
            )
        publisherRule.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(feature)))
    }

    /**
     * Publishes a TABLETOP posture: a half-opened, separating HORIZONTAL hinge that splits the
     * window into a top and bottom pane. Drives the `FoldableTabletopLayout` path.
     */
    fun publishTabletopPosture(activity: Activity) {
        val feature =
            FoldingFeature(
                activity = activity,
                state = State.HALF_OPENED,
                orientation = Orientation.HORIZONTAL,
            )
        publisherRule.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(feature)))
    }

    /**
     * Publishes a FLAT (fully unfolded / non-separating) posture by clearing all display
     * features. After this, hinge-aware splits collapse back to their single-pane
     * `standardContent`, matching a phone / fully-open tablet.
     */
    fun publishFlat() {
        publisherRule.overrideWindowLayoutInfo(TestWindowLayoutInfo(emptyList()))
    }
}
