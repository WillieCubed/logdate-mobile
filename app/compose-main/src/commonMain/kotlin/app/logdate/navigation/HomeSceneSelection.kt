package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategy
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.navigation.scenes.HomeSceneStrategy
import app.logdate.navigation.scenes.supportsDualPaneHomeScene
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.foldable.calculateFoldableSplitLayout
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.platform.currentPlatform
import org.koin.compose.koinInject

/**
 * Default [SceneStrategy] for the LogDate graph. Activates [HomeSceneStrategy]'s two-pane
 * layout when the current window is wide enough; otherwise the strategy returns `null` and
 * `NavDisplay` falls back to its single-pane default.
 *
 * iPad gets a softer threshold because standard iPad portrait widths (e.g. 768pt on a 10.2"
 * model, 834pt on an 11" Pro) sit just below the Material expanded breakpoint of 840dp. On
 * those devices users still expect a list/detail layout, so we drop the activation point to
 * the medium breakpoint (600dp) when the host is iPad. Narrow Split View widths stay below
 * that floor and continue to render single-pane.
 */
@Composable
internal fun rememberHomeSceneStrategy(): SceneStrategy<NavKey> {
    val flags: FeatureFlagStore = koinInject()
    val workspaceEnabled by remember(flags) { flags.observe(FeatureFlag.HOME_WORKSPACE_V2) }
        .collectAsState(initial = FeatureFlag.HOME_WORKSPACE_V2.defaultEnabled)
    val windowSize = LocalWindowInfo.current.containerSize
    val isIpad = currentPlatform.isIpad
    val foldableLayoutInfo = rememberFoldableLayoutInfo()
    val density = LocalDensity.current
    val supportsDualPane =
        with(density) {
            val widthDp = windowSize.width.toDp()
            val heightDp = windowSize.height.toDp()
            val defaultDualPane =
                supportsDualPaneHomeScene(width = widthDp, height = heightDp) ||
                    (isIpad && widthDp.value >= IPAD_DUAL_PANE_MIN_WIDTH_DP)
            supportsDualPaneHomeScene(
                width = widthDp,
                height = heightDp,
                foldableLayoutInfo = foldableLayoutInfo,
            ) ||
                (defaultDualPane && foldableLayoutInfo.hinge?.isSeparating != true)
        }
    val foldableSplitLayout =
        with(density) {
            calculateFoldableSplitLayout(
                containerWidth = windowSize.width.toDp(),
                containerHeight = windowSize.height.toDp(),
                layoutInfo = foldableLayoutInfo,
            )
        }
    val sceneSplitLayout =
        when (foldableSplitLayout) {
            is FoldableSplitLayout.Vertical -> foldableSplitLayout
            FoldableSplitLayout.None,
            is FoldableSplitLayout.Horizontal,
            -> FoldableSplitLayout.None
        }
    return remember(workspaceEnabled, supportsDualPane, sceneSplitLayout) {
        HomeSceneStrategy(
            supportsDualPane = { workspaceEnabled || supportsDualPane },
            foldableSplitLayout = { sceneSplitLayout },
        )
    }
}

/**
 * Lower bound (in dp) at which an iPad host should render the two-pane home layout. Set to
 * the Material medium breakpoint so iPad portrait fits comfortably while narrow Split View
 * widths still fall back to single-pane.
 */
private const val IPAD_DUAL_PANE_MIN_WIDTH_DP = 600
