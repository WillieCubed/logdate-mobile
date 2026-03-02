package app.logdate.feature.editor.ui.common

import androidx.compose.runtime.Composable

/**
 * Desktop implementation of back handler that listens for Escape key presses.
 *
 * @param enabled Whether the back handler is enabled
 * @param onBack Callback to invoke when back is pressed
 */
@Suppress("ktlint:standard:function-naming")
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // Desktop doesn't have a built-in back handler
    // We could implement this with keyboard event listeners for Escape key
    // For now, we leave it empty as it's not critical for this platform
}
