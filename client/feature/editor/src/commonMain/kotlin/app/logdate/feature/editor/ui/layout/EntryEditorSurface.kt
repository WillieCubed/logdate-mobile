package app.logdate.feature.editor.ui.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.AspectRatios

/**
 * A standardized surface for entry editors that maintains proper aspect ratio constraints.
 *
 * This component provides a consistent card-based surface for editors with appropriate
 * elevation and responsive height constraints based on the width.
 *
 * @param maxWidthDp Optional explicit maximum width to use for height constraints
 * @param content The composable content to display inside the editor surface
 * @param modifier Optional modifier for the card surface
 * @param containerColor The background color of the card (defaults to surfaceContainer)
 * @param elevation The elevation of the card surface (default: 4.dp)
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun EntryEditorSurface(
    modifier: Modifier = Modifier,
    maxWidthDp: Dp? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    elevation: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val calculatedMaxWidth = remember(maxWidth, maxWidthDp) { maxWidthDp ?: maxWidth }
        val maxHeight = remember(calculatedMaxWidth) { (calculatedMaxWidth / AspectRatios.RATIO_9_16).coerceAtMost(520.dp) }

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Apply height constraints based on the actual available pane/window width.
                    .heightIn(min = 120.dp, max = maxHeight),
            colors =
                CardDefaults.cardColors(
                    containerColor = containerColor,
                ),
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = elevation,
                ),
        ) {
            content()
        }
    }
}
