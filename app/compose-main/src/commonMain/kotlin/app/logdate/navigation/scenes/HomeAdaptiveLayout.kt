package app.logdate.navigation.scenes

import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.foldable.calculateFoldableSplitLayout

internal fun supportsDualPaneHomeScene(
    width: Dp,
    height: Dp,
): Boolean {
    val isLandscapeCompact =
        height.value < HEIGHT_DP_MEDIUM_LOWER_BOUND &&
            width.value >= WIDTH_DP_MEDIUM_LOWER_BOUND

    return width.value >= WIDTH_DP_EXPANDED_LOWER_BOUND || isLandscapeCompact
}

internal fun supportsDualPaneHomeScene(
    width: Dp,
    height: Dp,
    foldableLayoutInfo: FoldableLayoutInfo,
): Boolean {
    val splitLayout =
        calculateFoldableSplitLayout(
            containerWidth = width,
            containerHeight = height,
            layoutInfo = foldableLayoutInfo,
        )
    if (splitLayout is FoldableSplitLayout.Vertical) return true

    val separatingHinge = foldableLayoutInfo.hinge?.takeIf { it.isSeparating }
    if (separatingHinge?.orientation == FoldableHingeOrientation.Vertical) return false
    if (separatingHinge?.orientation == FoldableHingeOrientation.Horizontal) return false

    return supportsDualPaneHomeScene(width = width, height = height)
}

internal fun WindowSizeClass.supportsDualPaneHomeScene(): Boolean {
    val isLandscapeCompact =
        !isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND) &&
            isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    return isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) || isLandscapeCompact
}
