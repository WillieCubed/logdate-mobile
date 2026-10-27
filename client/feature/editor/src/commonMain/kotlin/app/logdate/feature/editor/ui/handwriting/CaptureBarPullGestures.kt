package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** The floating selector participates in the page's scroll and creation detent. */
internal fun Modifier.captureBarPullGestures(
    state: EntryPageOverviewState,
    enabled: Boolean,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(state) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, amount ->
                    change.consume()
                    state.captureDrag(amount)
                },
                onDragEnd = { state.releaseCaptureDrag() },
                onDragCancel = { state.pull = 0f },
            )
        }
    }
