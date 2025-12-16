package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A panel displaying a text journal note in story format.
 * 
 * Renders a journal text entry with date information and stylized presentation.
 * Designed to showcase written content in an elegant, readable format.
 * 
 * ## Visual Design:
 * - **Content Area**: Padded container with subtle background
 * - **Typography**: Clear, readable text styles with appropriate sizing
 * - **Date Display**: Secondary emphasis for contextual information
 * - **Background Options**: Supports both solid colors and image backgrounds
 * 
 * @param content The text content from the journal note
 * @param dateFormatted Formatted date string showing when the note was created
 * @param backgroundColor Solid background color (used when no image provided)
 * @param backgroundImageUri Optional background image URI
 * @param modifier Modifier for customizing the panel container
 */
@Composable
fun TextNotePanel(
    content: String,
    dateFormatted: String,
    backgroundColor: Color,
    backgroundImageUri: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background
        if (backgroundImageUri != null) {
            AsyncImage(
                model = backgroundImageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        } else {
            // Apply a gradient to the solid color background for visual interest
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor,
                                backgroundColor.copy(red = backgroundColor.red * 0.8f, green = backgroundColor.green * 0.8f, blue = backgroundColor.blue * 0.8f)
                            )
                        )
                    )
            )
        }
        
        // Content container
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Quote marks for styling
            Text(
                text = "\u201C",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Main journal content
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Date information
            Text(
                text = dateFormatted,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic
            )
        }
    }
}