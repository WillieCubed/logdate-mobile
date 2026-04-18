package app.logdate.wear.presentation.audio.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme

/**
 * Audio waveform visualization optimized for Wear OS.
 * Displays audio levels as vertical bars with a simplified design suitable for small screens.
 *
 * Draws levels directly on the Canvas without per-element animations to avoid creating
 * N independent animation states (one per audio sample), which causes severe frame drops
 * on constrained Wear OS hardware.
 */
@Composable
fun AudioWaveform(
    audioLevels: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    barWidth: Float = 3f,
) {
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(24.dp),
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (audioLevels.isEmpty() || audioLevels.size == 1) {
            val level = audioLevels.firstOrNull() ?: 0f
            val barCount = (width / (barWidth * 2)).toInt().coerceAtMost(15)

            for (i in 0 until barCount) {
                val distanceFromCenter = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                val barLevel = level * (1f - distanceFromCenter * 0.8f)
                val x = width * i / barCount + barWidth

                drawLine(
                    color = barColor,
                    start = Offset(x, centerY + height * 0.4f * barLevel),
                    end = Offset(x, centerY - height * 0.4f * barLevel),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        } else {
            val barSpacing = width / audioLevels.size.coerceAtLeast(1)

            audioLevels.forEachIndexed { index, level ->
                val x = barSpacing * (index + 0.5f)

                drawLine(
                    color = barColor,
                    start = Offset(x, centerY + height * 0.4f * level),
                    end = Offset(x, centerY - height * 0.4f * level),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
