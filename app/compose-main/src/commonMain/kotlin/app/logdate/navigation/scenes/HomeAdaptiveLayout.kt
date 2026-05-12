package app.logdate.navigation.scenes

import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

internal fun supportsDualPaneHomeScene(
    width: Dp,
    height: Dp,
): Boolean {
    val isLandscapeCompact =
        height.value < HEIGHT_DP_MEDIUM_LOWER_BOUND &&
            width.value >= WIDTH_DP_MEDIUM_LOWER_BOUND

    return width.value >= WIDTH_DP_EXPANDED_LOWER_BOUND || isLandscapeCompact
}

internal fun WindowSizeClass.supportsDualPaneHomeScene(): Boolean {
    val isLandscapeCompact =
        !isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND) &&
            isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    return isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) || isLandscapeCompact
}
