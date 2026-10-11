package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategy
import app.logdate.navigation.scenes.RelatedContentSceneStrategy
import app.logdate.navigation.scenes.SettingsActivitySceneStrategy
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.foldable.calculateFoldableSplitLayout
import app.logdate.ui.foldable.rememberFoldableLayoutInfo

@Composable
internal fun rememberRelatedContentSceneStrategy(): SceneStrategy<NavKey> {
    val geometry = rememberActivitySceneGeometry()
    return remember(geometry) {
        RelatedContentSceneStrategy(
            supportsDualPane = { geometry.supportsRelatedPanes },
        )
    }
}

@Composable
internal fun rememberSettingsActivitySceneStrategy(): SceneStrategy<NavKey> {
    val geometry = rememberActivitySceneGeometry()
    return remember(geometry) {
        SettingsActivitySceneStrategy(
            supportsDualPane = { true },
            foldableSplitLayout = { geometry.split },
        )
    }
}

internal data class ActivitySceneGeometry(
    val supportsRelatedPanes: Boolean,
    val split: FoldableSplitLayout,
)

@Composable
private fun rememberActivitySceneGeometry(): ActivitySceneGeometry {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val foldable = rememberFoldableLayoutInfo()
    return with(density) {
        val width = size.width.toDp()
        val height = size.height.toDp()
        activitySceneGeometry(width, height, foldable, fontScale)
    }
}

internal fun activitySceneGeometry(
    width: Dp,
    height: Dp,
    foldable: FoldableLayoutInfo,
    fontScale: Float,
): ActivitySceneGeometry {
    val scale = fontScale.coerceAtLeast(1f)
    val split = calculateFoldableSplitLayout(width, height, foldable, minPaneWidth = 0.dp, minPaneHeight = 0.dp)
    val supports =
        height >= 480.dp * scale &&
            when (split) {
                is FoldableSplitLayout.Vertical -> split.leftPane.width + split.rightPane.width >= 848.dp * scale
                is FoldableSplitLayout.Horizontal -> false
                FoldableSplitLayout.None -> foldable.hinge?.isSeparating != true && width >= 848.dp * scale
            }
    return ActivitySceneGeometry(supports, split)
}
