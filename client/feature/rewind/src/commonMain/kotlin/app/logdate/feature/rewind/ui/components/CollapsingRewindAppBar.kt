package app.logdate.feature.rewind.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A top app bar for the rewind screen that changes background color on scroll.
 * 
 * This component implements the Material Design spec for app bars that transition from transparent
 * to a surface color as the user scrolls. The app bar maintains its full height but changes
 * background color according to the scroll position.
 * 
 * @param title The title text to display in the app bar
 * @param scrollBehavior The scroll behavior that controls the app bar's appearance
 * @param modifier Additional modifiers for the app bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingRewindAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    // Calculate the scroll fraction (0.0 to 1.0) for color interpolation
    val scrollFraction by remember {
        derivedStateOf {
            // Get the offset fraction from the scroll behavior
            // When at the top: 0.0, when scrolled: approaches 1.0
            val fraction = scrollBehavior.state.overlappedFraction
            fraction.coerceIn(0f, 1f)  // Ensure it's within bounds
        }
    }
    
    // Interpolate between transparent (at top) and surface color (when scrolled)
    val backgroundColor = lerp(
        Color.Transparent,
        MaterialTheme.colorScheme.surface,
        scrollFraction
    )
    
    // Create color scheme that transitions based on scroll position
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = backgroundColor,
        scrolledContainerColor = MaterialTheme.colorScheme.surface
    )
    
    // Standard TopAppBar with our dynamic colors
    TopAppBar(
        title = { 
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        colors = colors,
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}