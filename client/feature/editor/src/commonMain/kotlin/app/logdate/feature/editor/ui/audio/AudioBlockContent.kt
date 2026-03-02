@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.editor.ui.audio

// import not needed as AnimatedPlayPauseButton is in the same package
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.audio.AudioContextProcessor
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.formatMediaDuration
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.delete
import logdate.client.feature.editor.generated.resources.no_audio_recorded_yet_use_the_microphone_button_to_begin_recording
import logdate.client.feature.editor.generated.resources.text_00_00
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

// Default height constants for audio visualizations
private val COLLAPSED_WAVEFORM_MIN_HEIGHT = 32.dp
private val EXPANDED_WAVEFORM_MIN_HEIGHT = 180.dp
private const val COLLAPSED_WAVEFORM_BARS = 15
private const val EXPANDED_WAVEFORM_BARS = 60

/**
 * A Material Design 3 component for displaying and interacting with audio blocks.
 * Features both collapsed and expanded states with shared element transitions.
 *
 * This composable handles all the UI rendering for audio blocks and passes events up through callbacks.
 * It manages transitions between states like play/pause and recording/playback internally.
 *
 * @param block The audio block data to display
 * @param isExpanded Whether the audio block is in expanded mode
 * @param onPlayPauseClicked Called when the play/pause button is clicked
 * @param onDeleteClicked Called when the delete button is clicked
 * @param onSeekPositionChanged Called when the playback position is changed by the user
 * @param playbackProgress Current playback progress (0.0f to 1.0f)
 * @param modifier Modifier for the root component
 */
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AudioBlockContent(
    block: AudioBlockUiState,
    isExpanded: Boolean,
    isPlaying: Boolean,
    onPlayPauseClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onSeekPositionChanged: (Float) -> Unit,
    playbackProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val audioContextProcessor: AudioContextProcessor = koinInject()
    val waveformAmplitudes by produceState(
        initialValue = emptyList<Float>(),
        block.uri,
        block.duration,
        block.timestamp,
        block.location,
    ) {
        val uri = block.uri
        if (uri.isNullOrBlank()) {
            value = emptyList()
            return@produceState
        }

        value =
            runCatching {
                audioContextProcessor
                    .process(
                        audioUri = uri,
                        durationMs = block.duration,
                        createdAt = block.timestamp,
                        latitude = block.location?.latitude,
                        longitude = block.location?.longitude,
                    ).amplitudes
            }.getOrElse { emptyList() }
    }

    // Track expanded state internally, with external override capability via isExpanded parameter
    var isCollapsedInternal by remember { mutableStateOf(!isExpanded) }

    // Animation state - transitioning between collapsed and expanded
    val transitionState =
        remember {
            MutableTransitionState(false).apply {
                targetState = isExpanded
            }
        }

    // Update target state when isExpanded changes
    LaunchedEffect(isExpanded) {
        transitionState.targetState = isExpanded
    }

    // Shared transition between collapsed and expanded states
    val transition = rememberTransition(transitionState, label = "AudioBlockTransition")

    val cornerRadius by transition.animateFloat(
        label = "CornerRadius",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow,
            )
        },
    ) { state ->
        if (state) 28f else 16f
    }

    // More dramatic scale change for play button
    val playButtonScale by transition.animateFloat(
        label = "PlayButtonScale",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy, // Bouncier animation
                stiffness = Spring.StiffnessLow,
            )
        },
    ) { state ->
        if (state) 1.4f else 1f // More dramatic scale change
    }

    // Update internal state when isExpanded changes
    LaunchedEffect(isExpanded) {
        isCollapsedInternal = !isExpanded
    }

    // The playback progress is now passed as a parameter directly from parent

    SharedTransitionLayout {
        AnimatedContent(
            targetState = !isCollapsedInternal,
            modifier = modifier,
        ) {
            if (!isCollapsedInternal) {
                ExpandedAudioContent(
                    block = block,
                    isPlaying = isPlaying,
                    progress = playbackProgress,
                    onPlayPauseClicked = onPlayPauseClicked,
                    onDeleteClicked = onDeleteClicked,
                    onProgressChanged = onSeekPositionChanged,
                    playButtonScale = playButtonScale,
                    waveformAmplitudes = waveformAmplitudes,
                    animatedVisibilityScope = this@AnimatedContent,
                    sharedTransitionScope = this@SharedTransitionLayout,
                )
            } else {
                CollapsedAudioContent(
                    block = block,
                    isPlaying = isPlaying,
                    onPlayPauseClicked = onPlayPauseClicked,
                    playButtonScale = playButtonScale,
                    waveformAmplitudes = waveformAmplitudes,
//                    modifier = Modifier.fillMaxWidth(),
                    animatedVisibilityScope = this@AnimatedContent,
                    sharedTransitionScope = this@SharedTransitionLayout,
                )
            }
        }
    }
}

/**
 * The collapsed state of the audio block - compact view with minimal controls.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun CollapsedAudioContent(
    block: AudioBlockUiState,
    isPlaying: Boolean,
    onPlayPauseClicked: () -> Unit,
    playButtonScale: Float,
    waveformAmplitudes: List<Float>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    with(sharedTransitionScope) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Play/Pause button - using shared element transition and animated shape
            AnimatedPlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPauseClicked,
                modifier =
                    Modifier
                        .size(40.dp)
                        .scale(playButtonScale)
                        .sharedElement(
                            rememberSharedContentState("play_pause_button_${block.id}"),
                            animatedVisibilityScope,
                        ),
                iconSize = 24.dp,
            )

            // Mini waveform visualization with shared element transition
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            RoundedCornerShape(16.dp),
                        ).sharedElement(
                            rememberSharedContentState("waveform_${block.id}"),
                            animatedVisibilityScope,
                        ),
            ) {
                // Only show waveform if we actually have audio data
                if (block.uri != null) {
                    // Simplified waveform for collapsed state that scales to fit container
                    AudioWaveformComponent(
                        audioLevels = waveformAmplitudes,
                        isRecording = false,
                        waveformColor = MaterialTheme.colorScheme.primary,
                        maxBars = COLLAPSED_WAVEFORM_BARS,
                        minHeight = COLLAPSED_WAVEFORM_MIN_HEIGHT,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Audio duration - only show if there's actual audio
            if (block.uri != null) {
                Text(
                    text = formatMediaDuration(block.duration, true),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(Res.string.text_00_00),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), // Dimmed to indicate placeholder
                )
            }
        }
    }
}

/**
 * The expanded state of the audio block - full controls, large waveform and additional options.
 * Makes use of vertical space for better visualization and controls.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun ExpandedAudioContent(
    block: AudioBlockUiState,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onProgressChanged: (Float) -> Unit,
    playButtonScale: Float,
    waveformAmplitudes: List<Float>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    with(sharedTransitionScope) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with title and delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Audio title or caption
                Text(
                    text = block.caption.ifEmpty { "Audio Recording" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Delete button
                IconButton(
                    onClick = onDeleteClicked,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(Res.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Large waveform visualization - fills remaining vertical space
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .sharedElement(
                            rememberSharedContentState("waveform_${block.id}"),
                            animatedVisibilityScope,
                        ),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                tonalElevation = 2.dp,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    // Only show waveform if we have actual audio data
                    if (block.uri != null) {
                        // Detailed waveform for expanded state that scales to container
                        AudioWaveformComponent(
                            audioLevels = waveformAmplitudes,
                            isRecording = false,
                            waveformColor = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp, // Slightly thicker lines for better visibility
                            maxBars = EXPANDED_WAVEFORM_BARS,
                            minHeight = EXPANDED_WAVEFORM_MIN_HEIGHT,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Play progress indicator
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(progress)
                                    .align(Alignment.CenterStart)
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    ),
                        )
                    } else {
                        // Show fun empty state with animation and engaging prompt
                        EmptyAudioBlockContent(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Centered play button overlay for easier access with animated shape
                    AnimatedPlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = onPlayPauseClicked,
                        modifier =
                            Modifier
                                .size(72.dp) // Larger button
                                .scale(playButtonScale)
                                .sharedElement(
                                    rememberSharedContentState("play_pause_button_${block.id}"),
                                    animatedVisibilityScope,
                                ),
                        iconSize = 40.dp,
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }

            // Playback controls - with different states based on whether there's audio content
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (block.uri != null) {
                    // Current position
                    Text(
                        text = formatMediaDuration((block.duration * progress).toLong(), true),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp),
                    )

                    // Playback progress slider (only enabled when there's audio)
                    Slider(
                        value = progress,
                        onValueChange = onProgressChanged,
                        modifier = Modifier.weight(1f),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    )

                    // Total duration
                    Text(
                        text = formatMediaDuration(block.duration, true),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .width(48.dp)
                                .testTag("audio_block_duration"),
                        textAlign = TextAlign.End,
                    )
                } else {
                    // Placeholder when no audio exists
                    Text(
                        text = stringResource(Res.string.no_audio_recorded_yet_use_the_microphone_button_to_begin_recording),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Transcription if available
            if (block.transcription.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(min = 80.dp), // Minimum height for transcription
                ) {
                    Text(
                        text = block.transcription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

// AnimatedPlayPauseButton moved to its own file

/**
 * A simplified wrapper around AnimatedPlayPauseButton that just displays the icon
 * without the button background.
 *
 * @param isPlaying Current playback state, true for playing (pause icon), false for paused (play icon)
 * @param iconSize Size of the icon in dp
 * @param modifier Optional modifier for the icon
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun PlayPauseAnimatedIcon(
    isPlaying: Boolean,
    iconSize: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        contentDescription = if (isPlaying) "Pause" else "Play",
        modifier = modifier.size(iconSize),
    )
}
