package app.logdate.feature.editor.ui.layout

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/**
 * An expandable toolbar that appears when the user overscrolls at the bottom of a list.
 * The toolbar is always rendered but with zero height when not visible.
 *
 * @param progress The expansion progress from 0.0 to 1.0
 * @param expanded Whether the toolbar is fully expanded
 * @param modifier Modifier for the toolbar container
 * @param content The content to display in the toolbar
 */
@Composable
fun ExpandableContentToolbar(
    progress: Float,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Target height based on progress or fully expanded state
    val targetHeight = if (expanded) 64f else (64f * progress).coerceIn(0f, 64f)
    
    // Animate height with bouncier spring physics when expanding/collapsing
    val height by animateFloatAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, // More bounce
            stiffness = Spring.StiffnessLow             // Slower, more playful movement
        ),
        label = "Toolbar height animation"
    )
    
    // Alpha follows progress directly
    val alpha = progress.coerceIn(0f, 1f)
    
    // Scale follows progress with more exaggerated range for bouncier feel
    val scale = 0.7f + (0.3f * progress.coerceIn(0f, 1f)) // Bigger scale change for more dramatic effect

    // Always render the toolbar with current height/alpha
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 2.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .alpha(alpha)
                .scale(scale)
                .align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(28.dp)
                    )
            ) {
                content()
            }
        }
    }
}