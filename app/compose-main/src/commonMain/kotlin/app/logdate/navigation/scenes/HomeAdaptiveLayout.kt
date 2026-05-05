package app.logdate.navigation.scenes

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

internal fun WindowSizeClass.supportsDualPaneHomeScene(): Boolean {
    val isLandscapeCompact =
        !isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND) &&
            isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    return isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) || isLandscapeCompact
}
