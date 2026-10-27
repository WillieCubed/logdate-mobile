@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.handwriting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.HandwritingDocument
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.handwriting_unreadable
import logdate.client.ui.generated.resources.original_handwriting
import org.jetbrains.compose.resources.stringResource

/** A reading surface for original strokes; it never changes the underlying document. */
@Composable
fun HandwritingPreview(
    document: HandwritingDocument,
    modifier: Modifier = Modifier,
    unreadable: Boolean = false,
) {
    if (unreadable) {
        androidx.compose.material3.Text(
            stringResource(Res.string.handwriting_unreadable),
            modifier.background(Color.White).padding(16.dp),
            color = Color.Black,
        )
        return
    }
    val description = stringResource(Res.string.original_handwriting)
    Canvas(modifier.clipToBounds().background(Color.White).semantics { contentDescription = description }) {
        val fit = minOf(size.width / document.width, size.height / document.height)
        withTransform({
            translate((size.width - document.width * fit) / 2f, (size.height - document.height * fit) / 2f)
            scale(fit, fit, Offset.Zero)
        }) {
            document.strokes.sortedBy { it.zIndex }.forEach { stroke ->
                val color =
                    runCatching { Color(stroke.color.removePrefix("#").toLong(16) or 0xFF000000) }
                        .getOrDefault(Color.Black)
                        .let { if (stroke.tool == "highlighter") it.copy(alpha = 0.35f) else it }
                withTransform({
                    translate(stroke.x, stroke.y)
                    rotate(stroke.rotation, Offset.Zero)
                    scale(stroke.scaleX, stroke.scaleY, Offset.Zero)
                }) {
                    if (stroke.points.size == 1) {
                        val point = stroke.points.first()
                        drawCircle(color, stroke.width * point.pressure.coerceIn(0.1f, 1f) / 2, Offset(point.x, point.y))
                    }
                    stroke.points.zipWithNext().forEach { (a, b) ->
                        drawLine(
                            color,
                            Offset(a.x, a.y),
                            Offset(b.x, b.y),
                            strokeWidth = stroke.width * ((a.pressure + b.pressure) / 2f).coerceIn(0.1f, 1f),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
