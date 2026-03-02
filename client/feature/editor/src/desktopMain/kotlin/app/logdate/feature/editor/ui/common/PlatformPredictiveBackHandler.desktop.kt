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
    // Desktop has no predictive back gesture; back is handled at the navigation layer.
}
