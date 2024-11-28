package app.logdate.ui.common

import androidx.compose.runtime.Composable

/**
 * Composable function that is called when the OS intercepts a back navigation event.
 *
 * @param enabled Whether the back handler should be enabled.
 * @param onBack The callback to run when the back event is intercepted.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)