package app.logdate.feature.postcards.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.uuid.Uuid

/**
 * Overlay that captures drag and transform gestures for the selected canvas element.
 *
 * Rendered on top of the viewport (in screen space). Converts screen-space gesture
 * deltas to canvas-space using the viewport scale factor.
 *
 * - Two-finger pinch/rotate transforms the selected element.
 * - Single-finger drag moves the selected element.
 * - Single tap deselects.
 */
@Composable
fun SelectionOverlay(
    selectedElementId: Uuid,
    viewportScale: Float,
    onBeginDrag: () -> Unit,
    onMoveElement: (elementId: Uuid, dx: Float, dy: Float) -> Unit,
    onEndDrag: () -> Unit,
    onTransformElement: (elementId: Uuid, scaleDelta: Float, rotationDelta: Float) -> Unit,
    onDeselect: () -> Unit,
) {
    val density = LocalDensity.current

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Two-finger transform gets highest priority
                .pointerInput(selectedElementId) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        onTransformElement(selectedElementId, zoom, rotation)
                        val dxCanvas = pan.x / (viewportScale * density.density)
                        val dyCanvas = pan.y / (viewportScale * density.density)
                        onMoveElement(selectedElementId, dxCanvas, dyCanvas)
                    }
                }
                // Single-finger drag for movement
                .pointerInput(selectedElementId) {
                    detectDragGestures(
                        onDragStart = { onBeginDrag() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dxCanvas = dragAmount.x / (viewportScale * density.density)
                            val dyCanvas = dragAmount.y / (viewportScale * density.density)
                            onMoveElement(selectedElementId, dxCanvas, dyCanvas)
                        },
                        onDragEnd = { onEndDrag() },
                        onDragCancel = { onEndDrag() },
                    )
                }
                // Tap to deselect — only fires when no drag/transform consumed the gesture
                .pointerInput(selectedElementId) {
                    detectTapGestures {
                        onDeselect()
                    }
                },
    )
}
