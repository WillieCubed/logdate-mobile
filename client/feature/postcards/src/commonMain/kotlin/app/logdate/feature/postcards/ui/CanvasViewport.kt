package app.logdate.feature.postcards.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/**
 * State holder for the canvas viewport's pan and zoom transforms.
 */
class CanvasViewportState {
    var offset by mutableStateOf(Offset.Zero)
        internal set
    var scale by mutableFloatStateOf(1f)
        internal set

    companion object {
        const val MIN_SCALE = 0.25f
        const val MAX_SCALE = 4f
    }
}

@Composable
fun rememberCanvasViewportState(): CanvasViewportState = remember { CanvasViewportState() }

/**
 * A pannable, zoomable viewport into the canvas coordinate space.
 *
 * Pan and zoom require two-finger gestures to prevent accidental canvas
 * movement from single-finger taps and drags (which are reserved for
 * element interaction).
 */
@Composable
fun CanvasViewport(
    state: CanvasViewportState,
    modifier: Modifier = Modifier,
    gestureEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val gestureModifier =
        if (gestureEnabled) {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        // Only pan/zoom with two or more fingers
                        if (pointerCount >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            state.scale =
                                (state.scale * zoom).coerceIn(
                                    CanvasViewportState.MIN_SCALE,
                                    CanvasViewportState.MAX_SCALE,
                                )
                            state.offset += pan

                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(gestureModifier)
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                },
    ) {
        content()
    }
}
