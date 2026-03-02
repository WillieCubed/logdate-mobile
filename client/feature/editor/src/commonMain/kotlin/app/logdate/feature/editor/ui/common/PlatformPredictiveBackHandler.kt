package app.logdate.feature.editor.ui.common

import androidx.compose.runtime.Composable

/**
 * Platform-specific predictive back handler.
 *
 * On Android, drives gesture progress so callers can seek a transition in real time,
 * then commits or cancels based on the gesture outcome.
 *
 * On iOS and Desktop, `onProgress` is never called; `onBack` fires on a plain back press.
 *
 * @param enabled Whether the handler intercepts back gestures
 * @param onProgress Called with a 0..1 fraction as the gesture progresses (Android only)
 * @param onBack Called when the back gesture is committed
 * @param onCancel Called when the back gesture is cancelled (Android only)
 */
@Suppress("ktlint:standard:function-naming")
@Composable
expect fun PlatformPredictiveBackHandler(
    enabled: Boolean = true,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
)
