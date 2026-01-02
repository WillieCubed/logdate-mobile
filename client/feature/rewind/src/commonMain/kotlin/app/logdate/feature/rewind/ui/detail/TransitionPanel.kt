package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A transition panel that connects story beats in a rewind.
 *
 * This panel creates narrative flow by bridging different moments and explaining
 * thematic shifts. It uses subtle styling to act as a connector between more
 * visually prominent content panels.
 *
 * ## Visual Design:
 * - **Typography**: Medium-sized, italic text for transitional feel
 * - **Background**: Subtle gradient to distinguish from main content
 * - **Styling**: Minimal decoration to avoid overwhelming the narrative
 * - **Purpose**: Guide the viewer through the story's progression
 *
 * ## Usage Examples:
 * - "But evenings shifted to culinary adventures"
 * - "Meanwhile, gaming became the escape you needed"
 * - "Then came a breakthrough"
 * - "And when evenings came"
 *
 * @param transitionText The transition text connecting story beats
 * @param modifier Modifier for customizing the panel container
 */
@Composable
fun TransitionPanel(
    transitionText: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = transitionText,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
        )
    }
}
