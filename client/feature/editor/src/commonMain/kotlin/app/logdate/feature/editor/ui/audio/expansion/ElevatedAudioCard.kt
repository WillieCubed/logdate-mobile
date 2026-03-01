package app.logdate.feature.editor.ui.audio.expansion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.audio.model.AudioSegment
import app.logdate.feature.editor.ui.audio.waveform.BezierAudioWaveform
import app.logdate.feature.editor.ui.formatMediaDuration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import logdate.client.feature.editor.generated.resources.*
import logdate.client.feature.editor.generated.resources.Res
/**
 * Second expansion state - elevated card with full controls.
 *
 * Floats above content with significant elevation, shows:
 * - Large waveform with segment markers
 * - Play/pause button
 * - Scrubber slider
 * - Skip forward/back buttons
 * - Duration display
 *
 * Long press triggers IMMERSIVE mode.
 */
@Composable
fun ElevatedAudioCard(
    amplitudes: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    palette: AudioPalette,
    durationMs: Long,
    createdAt: Instant,
    modifier: Modifier = Modifier,
    segments: List<AudioSegment> = emptyList(),
    onPlayPause: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onCrossSegment: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val elevation by animateFloatAsState(
        targetValue = 24f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevation"
    )

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(palette.waveformGradientStart).copy(alpha = 0.15f),
            Color(palette.waveformGradientEnd).copy(alpha = 0.08f)
        )
    )

    // Scrim background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            }
    ) {
        Surface(
            modifier = modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress() }
                    )
                },
            shape = RoundedCornerShape(24.dp),
            shadowElevation = elevation.dp,
            tonalElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .background(backgroundBrush)
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Time context
                    Text(
                        text = formatDateTime(createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Large waveform
                    BezierAudioWaveform(
                        amplitudes = amplitudes,
                        progress = progress,
                        palette = palette,
                        segments = segments,
                        durationMs = durationMs,
                        onSeek = onSeek,
                        onDragStart = onDragStart,
                        onDragEnd = onDragEnd,
                        onCrossSegment = onCrossSegment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    // Scrubber slider
                    Slider(
                        value = progress,
                        onValueChange = onSeek,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(palette.accentColor),
                            activeTrackColor = Color(palette.playedFillColor),
                            inactiveTrackColor = Color(palette.waveformGradientStart).copy(alpha = 0.3f)
                        )
                    )

                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatProgress(progress, durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMediaDuration(durationMs, false),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Playback controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSkipBack) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = stringResource(Res.string.skip_back_10_seconds),
                                tint = Color(palette.accentColor)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Large play/pause button
                        Surface(
                            onClick = onPlayPause,
                            shape = CircleShape,
                            color = Color(palette.accentColor),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(onClick = onSkipForward) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = stringResource(Res.string.skip_forward_10_seconds),
                                tint = Color(palette.accentColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatProgress(progress: Float, durationMs: Long): String {
    val currentMs = (progress * durationMs).toLong()
    return formatMediaDuration(currentMs, false)
}

private fun formatDateTime(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val hour = if (local.hour == 0) 12 else if (local.hour > 12) local.hour - 12 else local.hour
    val amPm = if (local.hour < 12) "AM" else "PM"
    return "${months[local.month.number - 1]} ${local.day}, $hour:${local.minute.toString().padStart(2, '0')} $amPm"
}
