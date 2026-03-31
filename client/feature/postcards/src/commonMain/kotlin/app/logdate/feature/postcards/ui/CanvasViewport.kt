package app.logdate.feature.postcards.ui

import androidx.compose.foundation.gestures.detectTransformGestures
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

/**
 * State holder for the canvas viewport's pan and zoom transforms.
 *
 * The viewport is a window into the unbounded canvas coordinate space.
 * [offset] is the pan position and [scale] is the zoom level.
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
 * Two-finger gestures pan and zoom the viewport. Content is rendered
 * inside the viewport using canvas coordinate transforms.
 *
 * @param state The viewport state holding current pan offset and zoom scale.
 * @param modifier Modifier for the viewport container.
 * @param content Composable content to render inside the viewport.
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
                detectTransformGestures { _, pan, zoom, _ ->
                    state.scale =
                        (state.scale * zoom).coerceIn(
                            CanvasViewportState.MIN_SCALE,
                            CanvasViewportState.MAX_SCALE,
                        )
                    state.offset += pan
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
