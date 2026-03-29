package app.logdate.feature.postcards.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.logdate.feature.postcards.model.CanvasBackground
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkTool
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.model.ShapeKind
import coil3.compose.AsyncImage

/**
 * Renders all elements of a [PostcardDocument] onto a Compose Canvas.
 *
 * Elements are drawn in [CanvasElement.zIndex] order. Each element is positioned,
 * rotated, and scaled according to its [CanvasElement.transform].
 *
 * @param document The Postcard document to render.
 * @param modifier Modifier for the renderer container.
 * @param onPhotoTap Callback when a photo element is tapped (for intertextuality navigation).
 */
@Composable
fun CanvasRenderer(
    document: PostcardDocument,
    modifier: Modifier = Modifier,
    onPhotoTap: ((CanvasElement.Photo) -> Unit)? = null,
) {
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) {
        // Background
        CanvasBackgroundLayer(document.background)

        // Elements sorted by z-index
        val sortedElements = document.elements.sortedBy { it.zIndex }
        for (element in sortedElements) {
            when (element) {
                is CanvasElement.Photo -> PhotoElementRenderer(element)
                is CanvasElement.Text -> TextElementRenderer(element)
                is CanvasElement.Ink -> InkElementRenderer(element)
                is CanvasElement.Shape -> ShapeElementRenderer(element)
                is CanvasElement.Sticker -> StickerElementRenderer(element)
            }
        }
    }
}

@Composable
private fun CanvasBackgroundLayer(background: CanvasBackground) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        when (background) {
            is CanvasBackground.SolidColor -> {
                drawRect(color = parseColor(background.value))
            }
            is CanvasBackground.Gradient -> {
                val colors = background.stops.map { parseColor(it.color) }
                if (colors.size >= 2) {
                    drawRect(
                        brush =
                            androidx.compose.ui.graphics.Brush
                                .verticalGradient(colors),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoElementRenderer(element: CanvasElement.Photo) {
    val transform = element.transform
    AsyncImage(
        model = element.mediaUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .offset(x = transform.x.dp, y = transform.y.dp)
                .graphicsLayer {
                    rotationZ = transform.rotation
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                }.size(200.dp, 200.dp),
    )
}

@Composable
private fun TextElementRenderer(element: CanvasElement.Text) {
    val transform = element.transform
    Text(
        text = element.content,
        style =
            TextStyle(
                color = parseColor(element.color),
                fontSize = element.fontSize.sp,
                fontFamily = resolveFontFamily(element.fontFamily),
            ),
        modifier =
            Modifier
                .offset(x = transform.x.dp, y = transform.y.dp)
                .graphicsLayer {
                    rotationZ = transform.rotation
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                },
    )
}

@Composable
private fun InkElementRenderer(element: CanvasElement.Ink) {
    val transform = element.transform
    val points = element.points
    if (points.size < 2) return

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .offset(x = transform.x.dp, y = transform.y.dp)
                .graphicsLayer {
                    rotationZ = transform.rotation
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                },
    ) {
        val path =
            Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }

        val alpha =
            when (element.tool) {
                InkTool.HIGHLIGHTER -> 0.4f
                else -> 1f
            }

        drawPath(
            path = path,
            color = parseColor(element.color).copy(alpha = alpha),
            style =
                Stroke(
                    width = element.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )
    }
}

@Composable
private fun ShapeElementRenderer(element: CanvasElement.Shape) {
    val transform = element.transform
    Canvas(
        modifier =
            Modifier
                .offset(x = transform.x.dp, y = transform.y.dp)
                .size(element.width.dp, element.height.dp)
                .graphicsLayer {
                    rotationZ = transform.rotation
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                },
    ) {
        val strokeStyle = Stroke(width = element.strokeWidth)
        val strokeColor = parseColor(element.color)
        val fill = element.fillColor?.let { parseColor(it) }

        when (element.shapeKind) {
            ShapeKind.RECTANGLE -> {
                if (fill != null) {
                    drawRoundRect(color = fill, style = Fill, cornerRadius = CornerRadius(4f, 4f))
                }
                drawRoundRect(color = strokeColor, style = strokeStyle, cornerRadius = CornerRadius(4f, 4f))
            }
            ShapeKind.CIRCLE -> {
                val radius = minOf(size.width, size.height) / 2
                if (fill != null) {
                    drawCircle(color = fill, radius = radius, style = Fill)
                }
                drawCircle(color = strokeColor, radius = radius, style = strokeStyle)
            }
            ShapeKind.LINE -> {
                drawLine(
                    color = strokeColor,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                    strokeWidth = element.strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            ShapeKind.ARROW -> {
                drawLine(
                    color = strokeColor,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                    strokeWidth = element.strokeWidth,
                    cap = StrokeCap.Round,
                )
                // Arrowhead
                val arrowSize = element.strokeWidth * 4
                val endX = size.width
                val endY = size.height
                val path =
                    Path().apply {
                        moveTo(endX, endY)
                        lineTo(endX - arrowSize, endY - arrowSize / 2)
                        lineTo(endX - arrowSize / 2, endY - arrowSize)
                        close()
                    }
                drawPath(path = path, color = strokeColor, style = Fill)
            }
        }
    }
}

@Composable
private fun StickerElementRenderer(element: CanvasElement.Sticker) {
    val transform = element.transform
    // Stickers are loaded from the sticker library by reference.
    // For now, render a placeholder — the actual URI resolution will be
    // wired up when the sticker repository is integrated.
    Box(
        modifier =
            Modifier
                .offset(x = transform.x.dp, y = transform.y.dp)
                .size(80.dp)
                .graphicsLayer {
                    rotationZ = transform.rotation
                    scaleX = transform.scaleX
                    scaleY = transform.scaleY
                },
    )
}

/**
 * Parses a hex color string (e.g., "#FF6B6B") into a Compose [Color].
 */
internal fun parseColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    return when (cleaned.length) {
        6 -> Color(("FF$cleaned").toLong(16))
        8 -> Color(cleaned.toLong(16))
        else -> Color.Black
    }
}

/**
 * Resolves a font family name from the document model to a Compose [FontFamily].
 */
internal fun resolveFontFamily(name: String): FontFamily {
    // Bundled personality fonts will be loaded from resources in a follow-up.
    // For now, map to system defaults as placeholders.
    return when (name) {
        "caveat" -> FontFamily.Cursive
        "dancing-script" -> FontFamily.Cursive
        "patrick-hand" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
}
