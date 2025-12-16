package app.logdate.feature.editor.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A surface component that provides consistent styling for cards.
 * This is a local implementation to avoid external dependencies.
 *
 * @param isFocused Whether the card is focused/expanded
 * @param isSelected Whether the card is selected
 * @param onClick Callback when the card is clicked
 * @param modifier Modifier for customizing the surface
 * @param content The content to display inside the surface
 */
@Composable
fun CardSurface(
    isFocused: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Calculate elevation based on focus state
    val elevation = if (isFocused) 6.dp else 1.dp
    
    // Calculate corner radius based on focus state
    val cornerRadius = if (isFocused) 16.dp else 8.dp
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cornerRadius),
        tonalElevation = elevation,
        shadowElevation = if (isFocused) 2.dp else 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        content()
    }
}