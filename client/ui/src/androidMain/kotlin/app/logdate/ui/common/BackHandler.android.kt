package app.logdate.ui.common

import androidx.compose.runtime.Composable

/**
 * Composable function that runs the provided [onBack] callback when the OS intercepts a back navigation event.
 *
 * This exists solely to wrap Android specific back navigation handling.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(
        enabled = enabled,
        onBack = onBack,
    )
}