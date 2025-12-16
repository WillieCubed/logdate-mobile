package app.logdate.feature.editor.ui.common

import androidx.compose.runtime.Composable

/**
 * Platform-specific back handler that intercepts system back button presses.
 * This needs to be implemented for each platform.
 *
 * @param enabled Whether the back handler is enabled
 * @param onBack Callback to invoke when back is pressed
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
)