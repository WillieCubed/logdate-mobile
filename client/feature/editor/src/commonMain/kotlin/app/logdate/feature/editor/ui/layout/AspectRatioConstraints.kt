package app.logdate.feature.editor.ui.layout

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Constrains the height of a composable based on its width and a maximum aspect ratio.
 * This is useful for maintaining proportional dimensions without forcing a fixed aspect ratio.
 *
 * Unlike aspectRatio() which enforces a specific ratio, this function only sets a maximum height
 * constraint based on the width and the provided aspect ratio.
 *
 * @param width The width value to use for calculating the max height
 * @param maxRatio The maximum aspect ratio (width/height) to apply
 * @return The modified Modifier with height constraint applied
 */
fun Modifier.constrainHeightByWidth(
    width: Dp,
    maxRatio: Float
): Modifier {
    return this.heightIn(max = width / maxRatio)
}