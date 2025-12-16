package app.logdate.feature.editor.ui.common

import androidx.compose.runtime.Composable

/**
 * iOS implementation of back handler.
 * On iOS, back navigation is typically handled by navigation gestures or navigation bars.
 *
 * @param enabled Whether the back handler is enabled
 * @param onBack Callback to invoke when back is pressed
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    // iOS doesn't have a built-in back handler in the same way Android does
    // Typically, back navigation is handled by swipe gestures or navigation bars
    // For now, we leave it empty as it's not critical for this platform
}