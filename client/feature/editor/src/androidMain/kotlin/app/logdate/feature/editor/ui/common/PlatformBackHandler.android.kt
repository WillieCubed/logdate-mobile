package app.logdate.feature.editor.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Android implementation of back handler that uses the AndroidX BackHandler.
 *
 * @param enabled Whether the back handler is enabled
 * @param onBack Callback to invoke when back is pressed
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    BackHandler(enabled = enabled, onBack = onBack)
}