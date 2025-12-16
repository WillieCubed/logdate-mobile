package app.logdate.feature.editor.ui.layout

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
import app.logdate.ui.PlatformDimensions
import app.logdate.ui.common.AspectRatios
import app.logdate.ui.common.constrainHeightRatioIn

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
@Composable
fun EntryEditorSurface(
    modifier: Modifier = Modifier,
    maxWidthDp: Dp? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    elevation: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    // If maxWidthDp is not provided, calculate the appropriate width based on screen size
    val screenWidth = PlatformDimensions.getScreenWidth()
    val calculatedMaxWidth = remember(screenWidth, maxWidthDp) {
        maxWidthDp ?: when {
            screenWidth < 600.dp -> screenWidth
            screenWidth < 900.dp -> 600.dp
            else -> 800.dp
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            // Apply height constraints based on width and aspect ratio
            .constrainHeightRatioIn(
                width = calculatedMaxWidth,
                maxRatio = AspectRatios.RATIO_9_16
            )
            // Add a minimum height to prevent collapsing when focused
            .heightIn(min = 120.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
        )
    ) {
        content()
    }
}