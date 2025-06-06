package app.logdate.ui.test

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A helper function that creates a test modifier with visible styling.
 * This is useful for testing if modifiers are properly passed and applied to screen components.
 *
 * @return A modifier with a colored border and background that is clearly visible
 */
@Composable
fun Modifier.createTestModifier(): Modifier {
    return this
        .clip(MaterialTheme.shapes.medium)
        .border(
            width = 4.dp,
            color = Color.Red.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.medium
        )
        .background(Color.Yellow.copy(alpha = 0.2f))
        .padding(8.dp)
}