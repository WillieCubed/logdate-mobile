package app.logdate.feature.editor.ui.common

import androidx.compose.runtime.Composable

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    // iOS handles back via swipe-from-edge navigation gestures at the navigation layer;
    // no in-composable hook needed here.
}
