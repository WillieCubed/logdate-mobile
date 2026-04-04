package app.logdate.feature.postcards.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool

/**
 * State for an in-progress ink stroke being drawn by the user.
 */
class InkCaptureState {
    var points by mutableStateOf<List<InkPoint>>(emptyList())
        internal set
    var isDrawing by mutableStateOf(false)
        internal set

    fun beginStroke(
        x: Float,
        y: Float,
        pressure: Float = 1f,
    ) {
        points = listOf(InkPoint(x, y, pressure))
        isDrawing = true
    }

    fun addPoint(
        x: Float,
        y: Float,
        pressure: Float = 1f,
    ) {
        if (isDrawing) {
            points = points + InkPoint(x, y, pressure)
        }
    }

    fun endStroke(): List<InkPoint> {
        isDrawing = false
        val result = points.toList()
        points = emptyList()
        return result
    }
}

@Composable
fun rememberInkCaptureState(): InkCaptureState = remember { InkCaptureState() }

/**
 * Overlay that captures ink strokes drawn by the user.
 *
 * When the ink tool is active, this overlay captures single-finger drag gestures
 * and records the path as a series of [InkPoint]s. The in-progress stroke is
 * rendered live as the user draws.
 *
 * @param tool The current ink tool (pen, highlighter, eraser).
 * @param color The stroke color as a hex string.
 * @param strokeWidth The stroke width in dp.
 * @param state The capture state tracking the in-progress stroke.
 * @param onStrokeComplete Called when the user lifts their finger, with the completed points.
 */
@Composable
fun InkCaptureOverlay(
    tool: InkTool,
    color: String,
    strokeWidth: Float,
    state: InkCaptureState = rememberInkCaptureState(),
    onStrokeComplete: (List<InkPoint>) -> Unit,
) {
    val parsedColor = parseColor(color)
    val alpha =
        when (tool) {
            InkTool.HIGHLIGHTER -> HIGHLIGHTER_ALPHA
            else -> 1f
        }

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(tool, color, strokeWidth) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val down = event.changes.firstOrNull() ?: continue
                            if (!down.pressed) continue

                            // Palm rejection: if a stylus started this stroke,
                            // ignore subsequent touch input from fingers.
                            val isStylus =
                                down.type == PointerType.Stylus ||
                                    down.type == PointerType.Eraser

                            state.beginStroke(
                                down.position.x,
                                down.position.y,
                                down.pressure,
                            )

                            while (true) {
                                val dragEvent = awaitPointerEvent()
                                val change = dragEvent.changes.firstOrNull() ?: break

                                // Reject finger input when a stylus stroke is active
                                if (isStylus && change.type == PointerType.Touch) {
                                    change.consume()
                                    continue
                                }

                                if (!change.pressed) {
                                    val points = state.endStroke()
                                    if (points.size >= 2) {
                                        onStrokeComplete(points)
                                    }
                                    break
                                }
                                state.addPoint(
                                    change.position.x,
                                    change.position.y,
                                    change.pressure,
                                )
                                change.consume()
                            }
                        }
                    }
                },
    ) {
        val points = state.points
        if (points.size >= 2) {
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val segmentWidth = strokeWidth * ((prev.pressure + curr.pressure) / 2f)
                drawLine(
                    color = parsedColor.copy(alpha = alpha),
                    start = Offset(prev.x, prev.y),
                    end = Offset(curr.x, curr.y),
                    strokeWidth = segmentWidth.coerceAtLeast(MIN_VISIBLE_STROKE_WIDTH),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Prevents strokes from becoming invisible at zero pressure. */
private const val MIN_VISIBLE_STROKE_WIDTH = 0.5f

/** Highlighter tool opacity. */
private const val HIGHLIGHTER_ALPHA = 0.4f
