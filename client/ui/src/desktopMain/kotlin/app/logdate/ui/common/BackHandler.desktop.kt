package app.logdate.ui.common

import androidx.compose.runtime.Composable

/**
 * Composable function that runs the provided [onBack] callback when the OS intercepts a back navigation event.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op on desktop
}