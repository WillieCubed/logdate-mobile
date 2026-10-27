@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import app.logdate.feature.postcards.ui.rememberInkCaptureState

/** Shared page coordinates keep inline, focused, and resized views of the same ink aligned. */
@Composable
internal fun HandwritingCanvas(
    page: HandwritingPage,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = false,
    allowTouch: Boolean = false,
    captureEnabled: Boolean = true,
    presentationProgress: Float = if (inputEnabled) 1f else 0f,
    previewViewportHeight: Float? = null,
    previewVerticalOffset: Float = 0f,
    focusedScrollOffset: Float = 0f,
    tool: InkTool = InkTool.PEN,
    inkColor: String = "#253457",
    strokeWidth: Float = 3f,
    ruled: Boolean = true,
    description: String,
    paperColor: Color = Color.White,
    fitPreviewToWidth: Boolean = false,
    adaptInkToPaper: Boolean = false,
    onStroke: (List<InkPoint>, InkTool) -> Unit = { _, _ -> },
) {
    val capture = rememberInkCaptureState()
    val completeStroke by rememberUpdatedState(onStroke)
    val input =
        if (!inputEnabled || !captureEnabled) {
            Modifier
        } else {
            Modifier.pointerInput(page.width, tool, allowTouch) {
                fun point(
                    position: Offset,
                    pressure: Float,
                ): InkPoint {
                    val scale = size.width / page.width
                    return InkPoint(position.x / scale, position.y / scale, pressure.coerceIn(0f, 1f))
                }
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down =
                            event.changes.firstOrNull {
                                it.pressed &&
                                    !it.previousPressed &&
                                    (it.type == PointerType.Stylus || it.type == PointerType.Eraser)
                            } ?: event.changes.firstOrNull {
                                it.pressed &&
                                    !it.previousPressed &&
                                    (it.type == PointerType.Mouse || (allowTouch && it.type == PointerType.Touch))
                            } ?: continue
                        val activeTool = if (down.type == PointerType.Eraser) InkTool.ERASER else tool
                        val start = point(down.position, down.pressure)
                        capture.beginStroke(start.x, start.y, start.pressure)
                        down.consume()
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) break
                                val next = point(change.position, change.pressure)
                                capture.addPoint(next.x, next.y, next.pressure)
                                change.consume()
                                if (!change.pressed) {
                                    completeStroke(capture.endStroke(), activeTool)
                                    break
                                }
                            }
                        } finally {
                            // A canceled pointer coroutine must never leave provisional ink behind.
                            capture.endStroke()
                        }
                    }
                }
            }
        }
    Canvas(
        modifier
            .clipToBounds()
            .then(input)
            .testTag(if (inputEnabled) "handwriting_canvas" else "handwriting_preview")
            .semantics {
                contentDescription = description
                if (inputEnabled && !captureEnabled) disabled()
            },
    ) {
        drawRect(paperColor)
        val viewportHeight = minOf(previewViewportHeight ?: size.height, size.height)
        val fitScale = if (fitPreviewToWidth) size.width / page.width else minOf(size.width / page.width, viewportHeight / page.height)
        val progress = presentationProgress.coerceIn(0f, 1f)
        val scale = fitScale + (size.width / page.width - fitScale) * progress
        val left = (size.width - page.width * fitScale) / 2f * (1f - progress)
        val top =
            if (fitPreviewToWidth) {
                0f
            } else {
                ((viewportHeight - page.height * fitScale) / 2f + previewVerticalOffset) * (1f - progress) - focusedScrollOffset * progress
            }
        translate(left, top) {
            if (ruled) {
                val ruleColor =
                    if (adaptInkToPaper &&
                        paperColor.luminance() < 0.35f
                    ) {
                        Color.White.copy(alpha = 0.10f)
                    } else {
                        Color(0xFFE5E9F1)
                    }
                var y = 96f
                while (y < if (inputEnabled || progress > 0f) maxOf(page.height, size.height / scale) else page.height) {
                    drawLine(ruleColor, Offset(24f * scale, y * scale), Offset((page.width - 24f) * scale, y * scale), scale)
                    y += 48f
                }
            }
            page.strokes.forEach { stroke ->
                drawInk(
                    stroke.points,
                    displayedInk(Color(stroke.color.removePrefix("#").toLong(16) or 0xFF000000), paperColor, adaptInkToPaper),
                    stroke.strokeWidth,
                    scale,
                    stroke.tool,
                )
            }
            if (tool != InkTool.ERASER) {
                drawInk(
                    capture.points,
                    displayedInk(Color(inkColor.removePrefix("#").toLong(16) or 0xFF000000), paperColor, adaptInkToPaper),
                    strokeWidth,
                    scale,
                    tool,
                )
            }
        }
    }
}

private fun DrawScope.drawInk(
    points: List<InkPoint>,
    color: Color,
    width: Float,
    scale: Float,
    tool: InkTool,
) {
    val paint = color.copy(alpha = if (tool == InkTool.HIGHLIGHTER) 0.35f else 1f)
    points.zipWithNext().forEach { (a, b) ->
        drawLine(
            color = paint,
            start = Offset(a.x * scale, a.y * scale),
            end = Offset(b.x * scale, b.y * scale),
            strokeWidth = (width * (a.pressure + b.pressure) / 2f).coerceAtLeast(0.6f) * scale,
            cap = StrokeCap.Round,
        )
    }
    if (points.size == 1) {
        points.firstOrNull()?.let {
            drawCircle(paint, (width * it.pressure).coerceAtLeast(0.6f) * scale / 2f, Offset(it.x * scale, it.y * scale))
        }
    }
}

private fun displayedInk(
    color: Color,
    paper: Color,
    adapt: Boolean,
): Color = if (adapt && paper.luminance() < 0.35f && color.luminance() < 0.4f) lerp(color, Color.White, 0.65f) else color
