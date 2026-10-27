package app.logdate.feature.editor.ui.handwriting

import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

/** Editable ink page. Coordinates stay in page units when the block changes size. */
@Serializable
data class HandwritingPage(
    val width: Float = 720f,
    val height: Float = 960f,
    val strokes: List<CanvasElement.Ink> = emptyList(),
) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    fun erase(
        path: List<InkPoint>,
        radius: Float,
    ): HandwritingPage {
        if (path.isEmpty()) return this
        return copy(
            strokes =
                strokes.filterNot { stroke ->
                    val threshold = radius + stroke.strokeWidth / 2f
                    val segments = stroke.points.zipWithNext().ifEmpty { stroke.points.map { it to it } }
                    val eraserSegments = path.zipWithNext().ifEmpty { path.map { it to it } }
                    segments.any { (a, b) ->
                        eraserSegments.any { (c, d) -> segmentsTouch(a, b, c, d, threshold) }
                    }
                },
        )
    }

    companion object {
        fun decode(content: String): HandwritingPage = Json.decodeFromString(serializer(), content)
    }
}

/** One undo transaction per completed pen or eraser gesture. */
data class HandwritingHistory(
    val page: HandwritingPage = HandwritingPage(),
    val past: List<HandwritingPage> = emptyList(),
    val future: List<HandwritingPage> = emptyList(),
) {
    fun edit(page: HandwritingPage): HandwritingHistory =
        if (page == this.page) this else HandwritingHistory(page, (past + this.page).takeLast(40))

    fun undo(): HandwritingHistory = past.lastOrNull()?.let { HandwritingHistory(it, past.dropLast(1), future + page) } ?: this

    fun redo(): HandwritingHistory = future.lastOrNull()?.let { HandwritingHistory(it, past + page, future.dropLast(1)) } ?: this
}

private fun segmentsTouch(
    a: InkPoint,
    b: InkPoint,
    c: InkPoint,
    d: InkPoint,
    radius: Float,
): Boolean {
    val abC = cross(a, b, c)
    val abD = cross(a, b, d)
    val cdA = cross(c, d, a)
    val cdB = cross(c, d, b)
    if (abC * abD < 0f && cdA * cdB < 0f) return true
    return minOf(distance(a, c, d), distance(b, c, d), distance(c, a, b), distance(d, a, b)) <= radius
}

private fun cross(
    a: InkPoint,
    b: InkPoint,
    p: InkPoint,
): Float = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)

private fun distance(
    p: InkPoint,
    a: InkPoint,
    b: InkPoint,
): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    val x = p.x - (a.x + t * dx)
    val y = p.y - (a.y + t * dy)
    return sqrt(x * x + y * y)
}
