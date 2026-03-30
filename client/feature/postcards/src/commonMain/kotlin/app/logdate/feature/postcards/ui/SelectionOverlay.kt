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
 * - Single-finger drag moves the selected element.
 * - Two-finger pinch/rotate transforms the selected element (scale + rotation).
 * - Tap on empty space deselects.
 *
 * @param selectedElementId The ID of the currently selected element.
 * @param viewportScale The current viewport zoom level, used for coordinate conversion.
 * @param onBeginDrag Called once at the start of a drag gesture (for undo batching).
 * @param onMoveElement Called with (elementId, dx, dy) in canvas coordinates during drag.
 * @param onEndDrag Called when the drag gesture ends.
 * @param onTransformElement Called with (elementId, scaleDelta, rotationDelta) during pinch/rotate.
 * @param onDeselect Called when the user taps on the overlay (to deselect).
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
                .pointerInput(selectedElementId) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        // Two-finger gesture: transform the selected element
                        onTransformElement(selectedElementId, zoom, rotation)
                        // Also apply the pan component as movement
                        val dxCanvas = pan.x / (viewportScale * density.density)
                        val dyCanvas = pan.y / (viewportScale * density.density)
                        onMoveElement(selectedElementId, dxCanvas, dyCanvas)
                    }
                }.pointerInput(selectedElementId) {
                    detectDragGestures(
                        onDragStart = { onBeginDrag() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Convert screen-space delta to canvas-space
                            val dxCanvas = dragAmount.x / (viewportScale * density.density)
                            val dyCanvas = dragAmount.y / (viewportScale * density.density)
                            onMoveElement(selectedElementId, dxCanvas, dyCanvas)
                        },
                        onDragEnd = { onEndDrag() },
                        onDragCancel = { onEndDrag() },
                    )
                }.pointerInput(selectedElementId) {
                    detectTapGestures {
                        onDeselect()
                    }
                },
    )
}
