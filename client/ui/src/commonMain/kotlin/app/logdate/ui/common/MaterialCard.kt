package app.logdate.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A standardized Material Card component that can be reused throughout the application.
 * This component enforces consistent styling while providing flexibility through parameters.
 *
 * Features:
 * - Optional title section
 * - Configurable shape, colors, and elevation
 * - Consistent padding and styling that follows Material Design guidelines
 * - Built on top of Material3 Card component
 *
 * @param modifier Modifier for customizing the card
 * @param title Optional title to display at the top of the card
 * @param titleStyle Text style for the title (defaults to titleMedium)
 * @param shape Shape of the card (defaults to RoundedCornerShape with 12dp radius)
 * @param containerColor Background color of the card (defaults to surface)
 * @param contentColor Text color within the card (defaults to onSurface)
 * @param elevation Elevation of the card (defaults to 2dp)
 * @param contentPadding Padding around the content (defaults to 16dp)
 * @param content The content to display inside the card
 */
@Composable
fun MaterialCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = 2.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            // Title section if provided
            title?.let {
                Text(
                    text = it,
                    style = titleStyle,
                    color = contentColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Card content
            content()
        }
    }
}