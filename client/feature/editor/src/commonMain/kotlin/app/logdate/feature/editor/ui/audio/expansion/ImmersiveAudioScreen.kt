@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.audio.expansion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.audio.model.AudioSegment
import app.logdate.feature.editor.ui.audio.AnimatedPlayPauseButton
import app.logdate.feature.editor.ui.audio.waveform.BezierAudioWaveform
import app.logdate.feature.editor.ui.formatMediaDuration
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.media.MediaDeviceSelector
import app.logdate.ui.platform.PlatformIcons
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
    detectedSounds: List<String> = emptyList(),
    onPlayPause: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onCrossSegment: () -> Unit = {},
    onClose: () -> Unit = {},
    outputSelection: MediaDeviceSelectionUiState? = null,
    onOutputDeviceSelected: (String) -> Unit = {},
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
                painter = PlatformIcons.close(),
                contentDescription = stringResource(Res.string.close),
                tint = onBackgroundColor.copy(alpha = 0.8f),
            )
        }

        FoldableTabletopLayout(
            modifier = Modifier.fillMaxSize(),
            topPane = {
                ImmersiveAudioContextPane(
                    amplitudes = amplitudes,
                    progress = progress,
                    palette = palette,
                    daylightPeriod = daylightPeriod,
                    durationMs = durationMs,
                    createdAt = createdAt,
                    segments = segments,
                    detectedSounds = detectedSounds,
                    onBackgroundColor = onBackgroundColor,
                    onSeek = onSeek,
                    onDragStart = {
                        controlsVisible = true
                        onDragStart()
                    },
                    onDragEnd = onDragEnd,
                    onCrossSegment = onCrossSegment,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp)
                            .statusBarsPadding(),
                )
            },
            bottomPane = {
                ImmersiveAudioControlsPane(
                    progress = progress,
                    isPlaying = isPlaying,
                    palette = palette,
                    durationMs = durationMs,
                    onBackgroundColor = onBackgroundColor,
                    controlsVisible = controlsVisible,
                    outputSelection = outputSelection,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onSkipBack = onSkipBack,
                    onSkipForward = onSkipForward,
                    onOutputDeviceSelected = onOutputDeviceSelected,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp)
                            .navigationBarsPadding(),
                )
            },
            fallback = {
                FoldableBookLayout(
                    modifier = Modifier.fillMaxSize(),
                    minPaneWidth = 320.dp,
                    startPane = {
                        ImmersiveAudioContextPane(
                            amplitudes = amplitudes,
                            progress = progress,
                            palette = palette,
                            daylightPeriod = daylightPeriod,
                            durationMs = durationMs,
                            createdAt = createdAt,
                            segments = segments,
                            detectedSounds = detectedSounds,
                            onBackgroundColor = onBackgroundColor,
                            onSeek = onSeek,
                            onDragStart = {
                                controlsVisible = true
                                onDragStart()
                            },
                            onDragEnd = onDragEnd,
                            onCrossSegment = onCrossSegment,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp)
                                    .statusBarsPadding()
                                    .navigationBarsPadding(),
                        )
                    },
                    endPane = {
                        ImmersiveAudioControlsPane(
                            progress = progress,
                            isPlaying = isPlaying,
                            palette = palette,
                            durationMs = durationMs,
                            onBackgroundColor = onBackgroundColor,
                            controlsVisible = controlsVisible,
                            outputSelection = outputSelection,
                            onPlayPause = onPlayPause,
                            onSeek = onSeek,
                            onSkipBack = onSkipBack,
                            onSkipForward = onSkipForward,
                            onOutputDeviceSelected = onOutputDeviceSelected,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp)
                                    .statusBarsPadding()
                                    .navigationBarsPadding(),
                        )
                    },
                    standardContent = {
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
                            ImmersiveAudioContextPane(
                                amplitudes = amplitudes,
                                progress = progress,
                                palette = palette,
                                daylightPeriod = daylightPeriod,
                                durationMs = durationMs,
                                createdAt = createdAt,
                                segments = segments,
                                detectedSounds = detectedSounds,
                                onBackgroundColor = onBackgroundColor,
                                onSeek = onSeek,
                                onDragStart = {
                                    controlsVisible = true
                                    onDragStart()
                                },
                                onDragEnd = onDragEnd,
                                onCrossSegment = onCrossSegment,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            ImmersiveAudioControlsPane(
                                progress = progress,
                                isPlaying = isPlaying,
                                palette = palette,
                                durationMs = durationMs,
                                onBackgroundColor = onBackgroundColor,
                                controlsVisible = controlsVisible,
                                outputSelection = outputSelection,
                                onPlayPause = onPlayPause,
                                onSeek = onSeek,
                                onSkipBack = onSkipBack,
                                onSkipForward = onSkipForward,
                                onOutputDeviceSelected = onOutputDeviceSelected,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun ImmersiveAudioContextPane(
    amplitudes: List<Float>,
    progress: Float,
    palette: AudioPalette,
    daylightPeriod: DaylightPeriod,
    durationMs: Long,
    createdAt: Instant,
    segments: List<AudioSegment>,
    detectedSounds: List<String>,
    onBackgroundColor: Color,
    onSeek: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onCrossSegment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

        AnimatedContent(
            targetState = detectedSounds,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ambient-sound-chips",
        ) { sounds ->
            if (sounds.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                AmbientSoundChips(
                    sounds = sounds,
                    contentColor = onBackgroundColor,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(MaterialTheme.shapes.large),
        )
    }
}

@Composable
private fun ImmersiveAudioControlsPane(
    progress: Float,
    isPlaying: Boolean,
    palette: AudioPalette,
    durationMs: Long,
    onBackgroundColor: Color,
    controlsVisible: Boolean,
    outputSelection: MediaDeviceSelectionUiState?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onOutputDeviceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                            painter = PlatformIcons.replay10(),
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
                            painter = PlatformIcons.forward10(),
                            contentDescription = stringResource(Res.string.skip_forward_10_seconds),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                outputSelection?.let { selection ->
                    Spacer(modifier = Modifier.height(16.dp))
                    MediaDeviceSelector(
                        selection = selection,
                        onDeviceSelected = onOutputDeviceSelected,
                        label = "Audio output",
                        modifier = Modifier.widthIn(max = 240.dp),
                    )
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

/**
 * A wrapping row of pill-shaped chips listing the ambient sounds the
 * on-device tagger detected on this recording. Tinted by the immersive
 * background's content color so they read against the gradient without an
 * extra theme dependency.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun AmbientSoundChips(
    sounds: List<String>,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (sound in sounds) {
            Surface(
                shape = RoundedCornerShape(50),
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor,
            ) {
                Text(
                    text = sound,
                    style = MaterialTheme.typography.labelMedium,
                    modifier =
                        Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 6.dp,
                        ),
                )
            }
        }
    }
}
