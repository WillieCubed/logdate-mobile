@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.audio.expansion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.audio.model.AudioPalette
import app.logdate.ui.audio.model.AudioSegment
import app.logdate.ui.platform.PlatformIcons
import kotlinx.coroutines.delay
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.close
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
            standardContent = {
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
