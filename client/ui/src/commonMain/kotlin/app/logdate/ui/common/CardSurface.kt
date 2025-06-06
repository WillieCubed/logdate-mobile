package app.logdate.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A reusable surface component that provides consistent styling and behavior
 * for card-like elements throughout the application.
 * 
 * Features:
 * - Animated elevation based on focused/expanded state
 * - Optional highlight border for selected state
 * - Consistent corner radius and padding
 * - Click handling with ripple effect
 * - Material3 theming support
 *
 * @param isFocused Whether the card is focused/expanded
 * @param isSelected Whether the card is selected (shows highlight)
 * @param onClick Callback when the surface is clicked
 * @param modifier Modifier for customizing the surface
 * @param cornerRadius Corner radius of the card (defaults to 12.dp)
 * @param elevationFocused Elevation when focused (defaults to 4.dp)
 * @param elevationUnfocused Elevation when not focused (defaults to 1.dp)
 * @param borderWidth Border width when selected (defaults to 2.dp)
 * @param content The content to display inside the surface
 */
@Composable
fun CardSurface(
    isFocused: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Float = 12f,
    elevationFocused: Float = 4f,
    elevationUnfocused: Float = 1f,
    borderWidth: Float = 2f,
    content: @Composable () -> Unit
) {
    // Animate elevation changes based on focused state
    val elevation by animateDpAsState(
        targetValue = if (isFocused) elevationFocused.dp else elevationUnfocused.dp,
        label = "elevation"
    )
    
    // Border width animation based on selected state
    val border by animateDpAsState(
        targetValue = if (isSelected) borderWidth.dp else 0.dp,
        label = "borderWidth"
    )
    
    val shape = RoundedCornerShape(cornerRadius.dp)
    
    // Apply styling and behavior to the container
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(elevation, shape)
            .clip(shape)
            .background(
                if (isFocused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = border,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
            .padding(12.dp)
    ) {
        // Render the provided content
        content()
    }
}