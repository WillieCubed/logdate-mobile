package app.logdate.feature.editor.ui.audio.expansion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.audio.model.AudioSegment
import app.logdate.feature.editor.ui.audio.AnimatedPlayPauseButton
import app.logdate.feature.editor.ui.audio.waveform.BezierAudioWaveform
import app.logdate.feature.editor.ui.formatMediaDuration
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.close
import logdate.client.feature.editor.generated.resources.skip_back_10_seconds
import logdate.client.feature.editor.generated.resources.skip_forward_10_seconds
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Full-screen immersive audio playback experience.
 *
 * Features:
 * - Edge-to-edge palette gradient background
 * - Large waveform visualization
 * - Auto-hiding controls (show on tap)
 * - Contextual time-of-day information
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun ImmersiveAudioScreen(
    amplitudes: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    palette: AudioPalette,
    daylightPeriod: DaylightPeriod,
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
    onClose: () -> Unit = {},
) {
    ImmersiveSystemBarEffect()

    var controlsVisible by remember { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }

    // Auto-hide controls after 4 seconds of playback
    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(4000)
            controlsVisible = false
        }
    }

    val backgroundColor = Color(palette.immersiveBackground)
    val onBackgroundColor = Color(palette.contentColor)

    val backgroundBrush =
        Brush.verticalGradient(
            colors =
                listOf(
                    backgroundColor,
                    Color(palette.waveformGradientStart).copy(alpha = 0.8f),
                    backgroundColor,
                ),
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    controlsVisible = !controlsVisible
                },
    ) {
        // Close button (always visible)
        IconButton(
            onClick = onClose,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.close),
                tint = onBackgroundColor.copy(alpha = 0.8f),
            )
        }

        // Main content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Time context (always visible)
            Text(
                text = formatDaylightPeriod(daylightPeriod),
                style = MaterialTheme.typography.labelLarge,
                color = onBackgroundColor.copy(alpha = 0.6f),
            )

            Text(
                text = formatDateTime(createdAt),
                style = MaterialTheme.typography.headlineSmall,
                color = onBackgroundColor,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Large waveform
            BezierAudioWaveform(
                amplitudes = amplitudes,
                progress = progress,
                palette = palette,
                segments = segments,
                durationMs = durationMs,
                onSeek = onSeek,
                onDragStart = {
                    controlsVisible = true
                    onDragStart()
                },
                onDragEnd = onDragEnd,
                onCrossSegment = onCrossSegment,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.large),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Auto-hiding controls
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Scrubber
                    Slider(
                        value = progress,
                        onValueChange = onSeek,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = Color(palette.accentColor),
                                activeTrackColor = Color(palette.playedFillColor),
                                inactiveTrackColor = onBackgroundColor.copy(alpha = 0.2f),
                            ),
                    )

                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatProgress(progress, durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = onBackgroundColor.copy(alpha = 0.7f),
                        )
                        Text(
                            text = formatMediaDuration(durationMs, false),
                            style = MaterialTheme.typography.bodySmall,
                            color = onBackgroundColor.copy(alpha = 0.7f),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Playback controls
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val skipButtonColors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(palette.accentColor).copy(alpha = 0.2f),
                                contentColor = Color(palette.accentColor),
                            )

                        FilledTonalIconButton(
                            onClick = onSkipBack,
                            colors = skipButtonColors,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = stringResource(Res.string.skip_back_10_seconds),
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        AnimatedPlayPauseButton(
                            isPlaying = isPlaying,
                            onClick = onPlayPause,
                            modifier = Modifier.size(80.dp),
                            iconSize = 48.dp,
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(palette.accentColor),
                                    contentColor = onBackgroundColor,
                                ),
                        )

                        Spacer(modifier = Modifier.width(24.dp))

                        FilledTonalIconButton(
                            onClick = onSkipForward,
                            colors = skipButtonColors,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = stringResource(Res.string.skip_forward_10_seconds),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatProgress(
    progress: Float,
    durationMs: Long,
): String {
    val currentMs = (progress * durationMs).toLong()
    return formatMediaDuration(currentMs, false)
}

private fun formatDateTime(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val months =
        listOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December",
        )
    val hour =
        if (local.hour == 0) {
            12
        } else if (local.hour > 12) {
            local.hour - 12
        } else {
            local.hour
        }
    val amPm = if (local.hour < 12) "AM" else "PM"
    return "${months[local.month.number - 1]} ${local.day}, ${local.year}\n$hour:${local.minute.toString().padStart(2, '0')} $amPm"
}

private fun formatDaylightPeriod(period: DaylightPeriod): String =
    when (period) {
        DaylightPeriod.DAWN -> "Dawn"
        DaylightPeriod.MORNING -> "Morning"
        DaylightPeriod.MIDDAY -> "Midday"
        DaylightPeriod.AFTERNOON -> "Afternoon"
        DaylightPeriod.GOLDEN_HOUR -> "Golden Hour"
        DaylightPeriod.EVENING -> "Evening"
        DaylightPeriod.NIGHT -> "Night"
    }
