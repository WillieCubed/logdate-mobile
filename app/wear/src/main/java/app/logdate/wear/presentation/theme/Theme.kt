package app.logdate.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun LogDateTheme(
    content: @Composable () -> Unit,
) {
    // Use built-in Wear OS Material 3 theme with default color scheme
    MaterialTheme(
        content = content
    )
}