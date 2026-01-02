package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A narrative context panel that sets the scene in a rewind story.
 *
 * This panel establishes the overall narrative or introduces thematic segments,
 * providing context and meaning to the moments that follow. It uses elegant
 * typography and optional background imagery to create an immersive storytelling
 * experience.
 *
 * ## Visual Design:
 * - **Typography**: Large, bold text for narrative impact
 * - **Background**: Optional image with gradient overlay for readability
 * - **Styling**: Subtle container with semi-transparent background
 * - **Alignment**: Centered for visual focus
 *
 * ## Usage Examples:
 * - Opening: "You finally made it to the California coast"
 * - Scene setting: "This week was rough - work deadlines converged"
 * - Resolution: "Sometimes the best trips are the ones you've dreamed about"
 *
 * @param contextText The narrative text that sets the scene
 * @param backgroundImageUri Optional background image URI for visual context
 * @param modifier Modifier for customizing the panel container
 */
@Composable
fun NarrativeContextPanel(
    contextText: String,
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
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        } else {
            // Subtle gradient background when no image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
            )
        }

        // Content container with subtle backdrop
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .padding(32.dp)
        ) {
            Text(
                text = contextText,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                lineHeight = MaterialTheme.typography.headlineLarge.lineHeight * 1.3f
            )
        }
    }
}
