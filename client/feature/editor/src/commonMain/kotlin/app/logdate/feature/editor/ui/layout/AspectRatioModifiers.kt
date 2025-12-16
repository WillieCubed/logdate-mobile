package app.logdate.feature.editor.ui.layout

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Constrains the height based on a reference width and aspect ratio(s).
 * This is useful for maintaining aspect ratios without forcing them, allowing the content
 * to adapt to available space while still respecting proportion constraints.
 *
 * The aspect ratio is expressed as width/height. For example:
 * - 16:9 ratio = 1.778... (widescreen landscape) 
 * - 4:3 ratio = 1.333... (traditional display)
 * - 9:16 ratio = 0.5625 (portrait phone)
 *
 * @param width The reference width to use for calculating height constraints
 * @param minRatio The minimum aspect ratio (width/height) to enforce, or null for no minimum
 * @param maxRatio The maximum aspect ratio (width/height) to enforce, or null for no maximum
 * @return A modified [Modifier] with height constraints based on the reference width and aspect ratio(s)
 */
fun Modifier.constrainHeightRatioIn(
    width: Dp,
    minRatio: Float? = null,
    maxRatio: Float? = null
): Modifier {
    // For width/height ratios:
    // - Larger ratio = shorter height (landscape)
    // - Smaller ratio = taller height (portrait)
    
    // Calculate min/max heights based on width and ratio
    val minHeight = if (maxRatio != null && maxRatio > 0f) width / maxRatio else null
    val maxHeight = if (minRatio != null && minRatio > 0f) width / minRatio else null
    
    return this.heightIn(
        min = minHeight ?: 0.dp,
        max = maxHeight ?: Dp.Infinity
    )
}

/**
 * Constrains the width based on a reference height and aspect ratio(s).
 * This is useful for maintaining aspect ratios without forcing them, allowing the content
 * to adapt to available space while still respecting proportion constraints.
 *
 * The aspect ratio is expressed as width/height. For example:
 * - 16:9 ratio = 1.778... (widescreen landscape)
 * - 4:3 ratio = 1.333... (traditional display)
 * - 9:16 ratio = 0.5625 (portrait phone)
 *
 * @param height The reference height to use for calculating width constraints
 * @param minRatio The minimum aspect ratio (width/height) to enforce, or null for no minimum
 * @param maxRatio The maximum aspect ratio (width/height) to enforce, or null for no maximum
 * @return A modified [Modifier] with width constraints based on the reference height and aspect ratio(s)
 */
fun Modifier.constrainWidthRatioIn(
    height: Dp,
    minRatio: Float? = null,
    maxRatio: Float? = null
): Modifier {
    // For width/height ratios:
    // - Larger ratio = wider width (landscape)
    // - Smaller ratio = narrower width (portrait)
    
    // Calculate min/max widths based on height and ratio
    val minWidth = if (minRatio != null && minRatio > 0f) height * minRatio else null
    val maxWidth = if (maxRatio != null && maxRatio > 0f) height * maxRatio else null
    
    return this.widthIn(
        min = minWidth ?: 0.dp,
        max = maxWidth ?: Dp.Infinity
    )
}