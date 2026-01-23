package app.logdate.feature.editor.ui.audio.waveform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * Generates smooth bezier curve paths from waveform amplitude data.
 *
 * Creates a mirrored waveform shape (upper and lower curves) suitable
 * for audio visualization with smooth transitions between samples.
 */
object WaveformPathGenerator {

    /**
     * Generates a closed path representing the waveform.
     *
     * @param amplitudes Normalized amplitude values (0.0 to 1.0)
     * @param width Total width of the waveform area
     * @param height Total height of the waveform area
     * @param smoothing Control point offset factor (0.0 to 0.5, default 0.2)
     * @return A closed Path representing the waveform
     */
    fun generatePath(
        amplitudes: List<Float>,
        width: Float,
        height: Float,
        smoothing: Float = 0.2f
    ): Path {
        if (amplitudes.isEmpty()) return Path()

        val centerY = height / 2f
        val maxAmplitude = height * 0.4f // Leave some padding

        // Generate upper curve points
        val upperPoints = amplitudes.mapIndexed { index, amplitude ->
            val x = (index.toFloat() / (amplitudes.size - 1).coerceAtLeast(1)) * width
            val y = centerY - (amplitude * maxAmplitude)
            Offset(x, y)
        }

        // Generate lower curve points (mirrored)
        val lowerPoints = upperPoints.map { point ->
            Offset(point.x, centerY + (centerY - point.y))
        }.reversed()

        return Path().apply {
            // Start at center left
            moveTo(0f, centerY)

            // Draw upper curve
            if (upperPoints.isNotEmpty()) {
                lineTo(upperPoints.first().x, upperPoints.first().y)
                drawSmoothCurve(upperPoints, smoothing)
            }

            // Draw lower curve (reversed)
            if (lowerPoints.isNotEmpty()) {
                drawSmoothCurve(lowerPoints, smoothing)
            }

            // Close the path
            close()
        }
    }

    /**
     * Draws a smooth bezier curve through the given points.
     */
    private fun Path.drawSmoothCurve(points: List<Offset>, smoothing: Float) {
        if (points.size < 2) return

        for (i in 0 until points.size - 1) {
            val current = points[i]
            val next = points[i + 1]

            // Calculate control points for smooth curve
            val controlX1 = current.x + (next.x - current.x) * smoothing
            val controlY1 = current.y
            val controlX2 = next.x - (next.x - current.x) * smoothing
            val controlY2 = next.y

            cubicTo(controlX1, controlY1, controlX2, controlY2, next.x, next.y)
        }
    }
}
