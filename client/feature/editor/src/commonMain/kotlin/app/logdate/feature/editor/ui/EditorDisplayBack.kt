package app.logdate.feature.editor.ui

import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.ui.platform.PlatformPredictiveBackHandler
import app.logdate.ui.platform.rememberSystemReduceMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal sealed interface EditorDisplay {
    data object Empty : EditorDisplay

    data class Expanded(
        val block: EntryBlockUiState,
    ) : EditorDisplay

    data object List : EditorDisplay
}

/** Keep the logical origin even when back begins before an expansion has settled. */
internal class EditorDisplayBackState {
    val progress = MutableStateFlow<Float?>(null)
    var origin: EditorDisplay? = null
    var surfaceProgressAtStart = 1f
}

@Composable
internal fun rememberEditorDisplayBack(
    transition: SeekableTransitionState<EditorDisplay>,
    display: EditorDisplay,
    destination: EditorDisplay,
    onDismiss: () -> Unit,
    surfaceProgress: () -> Float,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onPrepareToLeave: () -> Unit = {},
): EditorDisplayBackState {
    val scope = rememberCoroutineScope()
    val back = remember { EditorDisplayBackState() }
    val progress = back.progress
    val reduceMotion = rememberSystemReduceMotion().value
    LaunchedEffect(destination) {
        progress.collectLatest { fraction ->
            if (fraction != null) transition.seekTo(fraction, targetState = destination)
        }
    }
    PlatformPredictiveBackHandler(
        enabled = display is EditorDisplay.Expanded,
        onProgress = { fraction ->
            if (back.origin == null) {
                onPrepareToLeave()
                back.origin = display
                back.surfaceProgressAtStart = surfaceProgress()
            }
            val value = if (reduceMotion) 0f else fraction
            progress.value = value
            onProgress(value)
        },
        onBack = {
            if (back.origin == null) onPrepareToLeave()
            back.origin = null
            scope.launch {
                progress.value = null
                onCommit()
                if (reduceMotion) transition.snapTo(destination) else transition.animateTo(destination)
                onDismiss()
            }
        },
        onCancel = {
            val restore = back.origin ?: display
            back.origin = null
            scope.launch {
                progress.value = null
                onCancel()
                if (reduceMotion) transition.snapTo(restore) else transition.animateTo(restore)
            }
        },
    )
    return back
}
