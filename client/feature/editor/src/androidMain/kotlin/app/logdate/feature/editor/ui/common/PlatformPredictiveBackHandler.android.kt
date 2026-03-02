package app.logdate.feature.editor.ui.common

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlin.coroutines.cancellation.CancellationException

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { flow ->
        try {
            flow.collect { event -> onProgress(event.progress) }
            onBack()
        } catch (e: CancellationException) {
            onCancel()
        }
    }
}
