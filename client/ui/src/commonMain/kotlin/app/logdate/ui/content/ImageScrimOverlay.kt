@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A gradient overlay for images that improves text readability.
 *
 * Draws a vertical gradient from [topAlpha] to [bottomAlpha] using black.
 */
@Composable
fun ImageScrimOverlay(
    modifier: Modifier = Modifier,
    topAlpha: Float = 0f,
    bottomAlpha: Float = 0.6f,
) {
    val brush =
        remember(topAlpha, bottomAlpha) {
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color.Black.copy(alpha = topAlpha),
                        Color.Black.copy(alpha = bottomAlpha),
                    ),
            )
        }
    Box(modifier = modifier.fillMaxSize().background(brush))
}

/**
 * A gradient overlay for images with multiple alpha stops.
 *
 * Each value in [alphaStops] is used as the alpha for a black color stop
 * in a vertical gradient.
 */
@Composable
fun ImageScrimOverlay(
    alphaStops: List<Float>,
    modifier: Modifier = Modifier,
) {
    val brush =
        remember(alphaStops) {
            Brush.verticalGradient(
                colors = alphaStops.map { Color.Black.copy(alpha = it) },
            )
        }
    Box(modifier = modifier.fillMaxSize().background(brush))
}
