package app.logdate.ui.tokens

import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle

/**
 * The record button's change from a circle, ready to record, to a rounded square, Stop. The phone
 * and the watch draw the same morph so the button looks the same on both.
 *
 * Both shapes are centered on the origin in shape units: the circle has radius 1, the square is
 * [STOP_SIDE] wide with corners of radius [STOP_CORNER]. Progress 0 is the circle, 1 the square.
 */
object RecordShape {
    const val STOP_SIDE = 1.2f
    const val STOP_CORNER = 0.3f
    private const val CIRCLE_VERTICES = 8

    val record: RoundedPolygon = RoundedPolygon.circle(numVertices = CIRCLE_VERTICES)

    val stop: RoundedPolygon =
        RoundedPolygon.rectangle(width = STOP_SIDE, height = STOP_SIDE, rounding = CornerRounding(radius = STOP_CORNER))

    val morph: Morph = Morph(start = record, end = stop)

    /**
     * The morph's outline at [progress], scaled so the circle fills a [size] by [size] box from the
     * origin. Reuses [path] so a button can redraw every frame without allocating.
     */
    fun outline(
        progress: Float,
        size: Float,
        path: Path = Path(),
    ): Path {
        path.rewind()
        val half = size / 2f
        var started = false
        morph.forEachCubic(progress.coerceIn(0f, 1f)) { cubic ->
            if (!started) {
                path.moveTo(half + cubic.anchor0X * half, half + cubic.anchor0Y * half)
                started = true
            }
            path.cubicTo(
                half + cubic.control0X * half,
                half + cubic.control0Y * half,
                half + cubic.control1X * half,
                half + cubic.control1Y * half,
                half + cubic.anchor1X * half,
                half + cubic.anchor1Y * half,
            )
        }
        path.close()
        return path
    }
}
