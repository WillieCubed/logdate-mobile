package app.logdate.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import app.logdate.ui.theme.Spacing

/**
 * Creates PaddingValues that centers content vertically by adding extra top and bottom padding.
 * Useful for lists where you want the first and last items to appear centered on screen.
 *
 * @param horizontal Horizontal padding (applied to start and end)
 * @param vertical Base vertical padding
 * @param extraVertical Additional vertical padding to center content
 */
@Composable
fun centeredListPadding(
    horizontal: Dp = Spacing.lg,
    vertical: Dp = Spacing.lg,
    extraVertical: Dp = Spacing.xxxl * 2
): PaddingValues {
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = vertical + extraVertical,
        bottom = vertical + extraVertical
    )
}

/**
 * Creates PaddingValues for grids that ensures content doesn't run against screen edges
 * and provides proper spacing for first/last items.
 *
 * @param minHorizontal Minimum horizontal padding
 */
@Composable
fun centeredGridPadding(
    minHorizontal: Dp = Spacing.xl
): PaddingValues {
    return PaddingValues(
        start = minHorizontal,
        end = minHorizontal,
        top = Spacing.lg + Spacing.xl,
        bottom = Spacing.lg + Spacing.xl
    )
}