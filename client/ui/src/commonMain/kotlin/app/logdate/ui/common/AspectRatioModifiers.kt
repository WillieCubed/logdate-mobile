package app.logdate.ui.common

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Constrains the height of a composable based on its width and a maximum aspect ratio.
 * This is useful for ensuring proportional dimensions without forcing a fixed aspect ratio.
 *
 * Unlike aspectRatio() which enforces a specific ratio, this function only sets a maximum height
 * constraint based on the width and the provided aspect ratio.
 *
 * @param width The width value to use for calculating the max height
 * @param maxRatio The maximum aspect ratio (width/height) to apply
 * @return The modified Modifier with height constraint applied
 */
fun Modifier.constrainHeightRatioIn(
    width: Dp,
    maxRatio: Float
): Modifier {
    return this.heightIn(max = width / maxRatio)
}

/**
 * Constrains the width of a composable based on its height and a minimum aspect ratio.
 * This is useful for ensuring proportional dimensions without forcing a fixed aspect ratio.
 *
 * Unlike aspectRatio() which enforces a specific ratio, this function only sets a minimum width
 * constraint based on the height and the provided aspect ratio.
 *
 * @param height The height value to use for calculating the min width
 * @param minRatio The minimum aspect ratio (width/height) to apply
 * @return The modified Modifier with width constraint applied
 */
fun Modifier.constrainWidthRatioIn(
    height: Dp,
    minRatio: Float
): Modifier {
    return this.widthIn(min = height * minRatio)
}

/**
 * Applies an aspect ratio constraint with additional max height constraint.
 * This is useful for maintaining a specific aspect ratio while also limiting
 * the maximum height based on the width.
 *
 * @param width The width value to use for calculating the max height
 * @param maxRatio The maximum aspect ratio (width/height) to apply
 * @param matchHeightConstraintsFirst Whether to match height constraints first (default: false)
 * @return The modified Modifier with aspect ratio constraints applied
 */
fun Modifier.aspectRatioIn(
    width: Dp,
    maxRatio: Float,
    matchHeightConstraintsFirst: Boolean = false
): Modifier {
    return this
        // First ensure a max height based on width and aspect ratio
        .heightIn(max = width / maxRatio)
        // Then apply the aspect ratio constraint
        .aspectRatio(
            ratio = 1f / maxRatio,
            matchHeightConstraintsFirst = matchHeightConstraintsFirst
        )
}