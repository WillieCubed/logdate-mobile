package app.logdate.feature.editor.ui.audio.waveform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.audio.model.AudioSegment
import kotlin.math.abs

/**
 * A bezier-smoothed audio waveform component with contextual color palette.
 *
 * Features:
 * - Smooth bezier curves for visual polish
 * - Gradient fills based on time-of-day palette
 * - Interactive scrubbing with segment indicators
 * - Visual playhead tracking
 *
 * @param amplitudes Normalized amplitude values (0.0 to 1.0)
 * @param progress Current playback progress (0.0 to 1.0)
 * @param palette Color palette based on recording time-of-day
 * @param modifier Compose modifier
 * @param segments Detected audio segments for haptic feedback points
 * @param durationMs Total duration in milliseconds (for segment positioning)
 * @param onSeek Callback when user seeks to a position
 * @param onDragStart Callback when user starts dragging
 * @param onDragEnd Callback when user stops dragging
 * @param onCrossSegment Callback when user drags across a segment boundary
 */
@Composable
fun BezierAudioWaveform(
    amplitudes: List<Float>,
    progress: Float,
    palette: AudioPalette,
    modifier: Modifier = Modifier,
    segments: List<AudioSegment> = emptyList(),
    durationMs: Long = 0L,
    onSeek: ((Float) -> Unit)? = null,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onCrossSegment: (() -> Unit)? = null
) {
    // Create gradient brushes from palette
    val unplayedBrush = remember(palette) {
        Brush.verticalGradient(
            colors = listOf(
                Color(palette.waveformGradientEnd).copy(alpha = 0.3f),
                Color(palette.waveformGradientStart).copy(alpha = 0.3f)
            )
        )
    }

    val playedBrush = remember(palette) {
        Brush.verticalGradient(
            colors = listOf(
                Color(palette.waveformGradientEnd),
                Color(palette.waveformGradientStart)
            )
        )
    }

    val accentColor = remember(palette) { Color(palette.accentColor) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onSeek != null) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val seekPosition = (offset.x / size.width).coerceIn(0f, 1f)
                                onSeek(seekPosition)
                            }
                        }
                        .pointerInput(segments, durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { onDragStart?.invoke() },
                                onDragEnd = { onDragEnd?.invoke() },
                                onHorizontalDrag = { change, _ ->
                                    val dragPosition = (change.position.x / size.width).coerceIn(0f, 1f)

                                    // Check if we crossed a segment boundary
                                    if (durationMs > 0 && segments.isNotEmpty()) {
                                        val currentMs = (dragPosition * durationMs).toLong()
                                        val nearSegment = segments.any { segment ->
                                            abs(segment.timestampMs - currentMs) < 100
                                        }
                                        if (nearSegment) {
                                            onCrossSegment?.invoke()
                                        }
                                    }

                                    onSeek(dragPosition)
                                }
                            )
                        }
                } else Modifier
            )
    ) {
        // Draw placeholder line if no amplitudes
        if (amplitudes.isEmpty()) {
            drawLine(
                color = accentColor.copy(alpha = 0.3f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2f
            )
            return@Canvas
        }

        // Generate the waveform path
        val path = WaveformPathGenerator.generatePath(amplitudes, size.width, size.height)

        // Draw unplayed portion (full waveform, dimmed)
        drawPath(path, brush = unplayedBrush)

        // Draw played portion (clipped to progress)
        clipRect(right = size.width * progress) {
            drawPath(path, brush = playedBrush)
        }

        // Draw playhead
        val playheadX = size.width * progress

        // Glow effect
        drawLine(
            color = accentColor.copy(alpha = 0.3f),
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, size.height),
            strokeWidth = 8f
        )

        // Main playhead line
        drawLine(
            color = accentColor,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, size.height),
            strokeWidth = 3f
        )

        // Draw segment markers
        if (durationMs > 0) {
            segments.forEach { segment ->
                val x = (segment.timestampMs.toFloat() / durationMs) * size.width
                drawCircle(
                    color = accentColor.copy(alpha = 0.5f),
                    radius = 4f,
                    center = Offset(x, size.height / 2)
                )
            }
        }
    }
}
