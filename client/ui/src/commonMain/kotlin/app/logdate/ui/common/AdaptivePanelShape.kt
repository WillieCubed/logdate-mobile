@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.ui.theme.Spacing

/**
 * Returns an animated [Shape] for content panel surfaces.
 *
 * - **Portrait phone**: Rounded top corners, flat bottom (panel extends to screen edge).
 * - **Landscape phone**: All corners rounded (panel sits inside a padded two-pane layout).
 * - **Tablet / desktop**: All corners flat (panel fills the containing pane, which clips).
 */
@Composable
fun adaptivePanelShape(
    fallbackWidth: Dp,
    fallbackHeight: Dp,
): Shape {
    val isInspectionMode = LocalInspectionMode.current
    val windowSizeClass =
        if (isInspectionMode) {
            null
        } else {
            currentWindowAdaptiveInfo().windowSizeClass
        }
    val isWide =
        windowSizeClass?.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
            ?: (fallbackWidth >= WIDTH_DP_EXPANDED_LOWER_BOUND.dp)
    val isTall =
        windowSizeClass?.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)
            ?: (fallbackHeight >= HEIGHT_DP_MEDIUM_LOWER_BOUND.dp)

    val topCorner by animateDpAsState(
        targetValue = if (isWide && isTall) 0.dp else Spacing.lg,
        animationSpec = tween(300),
        label = "PanelTopCornerRadius",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (isWide && !isTall) Spacing.lg else 0.dp,
        animationSpec = tween(300),
        label = "PanelBottomCornerRadius",
    )

    return remember(topCorner, bottomCorner) {
        RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner,
        )
    }
}
