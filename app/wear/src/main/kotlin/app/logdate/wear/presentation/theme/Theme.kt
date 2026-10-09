package app.logdate.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme

/**
 * The watch's theme: Material 3 Expressive motion, so shape morphs, presses and list transforms move
 * with springs instead of fixed tweens. Colors stay the platform's defaults.
 */
@Composable
fun LogDateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
