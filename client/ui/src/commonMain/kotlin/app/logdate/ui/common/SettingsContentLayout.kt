package app.logdate.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A standardized content container for settings screens that provides consistent layout constraints
 * and improved readability across different screen sizes.
 * 
 * ## Purpose
 * 
 * This container enforces a maximum width of 600dp for settings content to ensure optimal
 * readability on large screens while maintaining full width on smaller devices. The content
 * is automatically centered horizontally when the max width constraint is active.
 * 
 * ## Layout Behavior
 * 
 * - **Small screens (≤600dp)**: Content fills the available width
 * - **Large screens (>600dp)**: Content is constrained to 600dp and centered
 * - **Vertical layout**: Content aligns to the top of the container
 * 
 * ## Usage
 * 
 * Wrap your settings screen's main content (typically a LazyColumn or Column) with this container:
 *
 * ```kotlin
 * DefaultSettingsContentContainer {
 *     LazyColumn {
 *         // Your settings content here
 *     }
 * }
 * ```
 * 
 * 
 * @param modifier Modifier to be applied to the outer container
 * @param content The settings content to display within the constrained layout
 */
@Composable
fun DefaultSettingsContentContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
        ) {
            content()
        }
    }
}

