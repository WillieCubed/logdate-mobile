package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.client.media.audio.transcription.TimedTranscript
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.formatMediaDuration
import app.logdate.ui.audio.AudioWaveformComponent
import app.logdate.ui.media.MediaDeviceSelector
import app.logdate.ui.platform.PlatformIcons
import logdate.client.ui.generated.resources.common_delete
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/** Completed recording presentation. Media extraction and playback remain with the editor adapter. */
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AudioBlockContent(
    block: AudioBlockUiState,
    isExpanded: Boolean,
    isPlaying: Boolean,
    timedTranscript: TimedTranscript? = null,
    onPlayPauseClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onSeekPositionChanged: (Float) -> Unit,
    onSeekTimestampClicked: (Long) -> Unit,
    playbackProgress: Float = 0f,
    outputSelection: MediaDeviceSelectionUiState? = null,
    onOutputDeviceSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    showDeleteAction: Boolean = true,
    waveformAmplitudes: List<Float> = emptyList(),
) {
    val sharedScope = LocalSharedTransitionScope.current
    val content: @Composable SharedTransitionScope.() -> Unit = {
        AnimatedContent(targetState = isExpanded, modifier = modifier, label = "CompletedAudioExpansion") { expanded ->
            val playbackModifier =
                Modifier.sharedElement(
                    rememberSharedContentState("play_pause_button_${block.id}"),
                    this@AnimatedContent,
                )
            val waveformModifier =
                Modifier.sharedElement(
                    rememberSharedContentState("waveform_${block.id}"),
                    this@AnimatedContent,
                )
            if (expanded) {
                ExpandedAudioContent(
                    block = block,
                    isPlaying = isPlaying,
                    progress = playbackProgress.coerceIn(0f, 1f),
                    timedTranscript = timedTranscript,
                    onPlayPauseClicked = onPlayPauseClicked,
                    onDeleteClicked = onDeleteClicked,
                    onProgressChanged = onSeekPositionChanged,
                    onSeekTimestampClicked = onSeekTimestampClicked,
                    outputSelection = outputSelection,
                    onOutputDeviceSelected = onOutputDeviceSelected,
                    waveformAmplitudes = waveformAmplitudes,
                    showDeleteAction = showDeleteAction,
                    playbackModifier = playbackModifier,
                    waveformModifier = waveformModifier,
                )
            } else {
                CollapsedAudioContent(
                    block,
                    isPlaying,
                    timedTranscript,
                    onPlayPauseClicked,
                    waveformAmplitudes,
                    playbackModifier,
                    waveformModifier,
                )
            }
        }
    }
    if (sharedScope == null) {
        SharedTransitionLayout(content = content)
    } else {
        content(sharedScope)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CollapsedAudioContent(
    block: AudioBlockUiState,
    isPlaying: Boolean,
    timedTranscript: TimedTranscript?,
    onPlayPauseClicked: () -> Unit,
    waveformAmplitudes: List<Float>,
    playbackModifier: Modifier,
    waveformModifier: Modifier,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompletedPlaybackButton(isPlaying, onPlayPauseClicked, Modifier.size(48.dp).then(playbackModifier))
            CompletedWaveform(waveformAmplitudes, 0f, Modifier.weight(1f).height(48.dp).then(waveformModifier))
            Text(
                text = formatMediaDuration(block.duration, true),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val transcriptText = timedTranscript?.plainText?.takeIf(String::isNotBlank) ?: block.transcription
        if (transcriptText.isNotBlank()) {
            Text(
                text = transcriptText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ExpandedAudioContent(
    block: AudioBlockUiState,
    isPlaying: Boolean,
    progress: Float,
    timedTranscript: TimedTranscript?,
    onPlayPauseClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onProgressChanged: (Float) -> Unit,
    onSeekTimestampClicked: (Long) -> Unit,
    outputSelection: MediaDeviceSelectionUiState?,
    onOutputDeviceSelected: (String) -> Unit,
    waveformAmplitudes: List<Float>,
    showDeleteAction: Boolean,
    playbackModifier: Modifier,
    waveformModifier: Modifier,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showDeleteAction) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (block.caption.isNotBlank()) {
                    Text(
                        block.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(Modifier.weight(1f))
                }
                IconButton(onClick = onDeleteClicked) {
                    Icon(PlatformIcons.delete(), stringResource(UiRes.string.common_delete))
                }
            }
        }
        if (outputSelection != null &&
            outputSelection.devices
                .filter { it.isAvailable }
                .distinctBy { it.groupKey }
                .size > 1
        ) {
            MediaDeviceSelector(outputSelection, onOutputDeviceSelected, label = "Audio output")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompletedPlaybackButton(isPlaying, onPlayPauseClicked, Modifier.size(64.dp).then(playbackModifier))
            CompletedWaveform(waveformAmplitudes, progress, Modifier.weight(1f).height(72.dp).then(waveformModifier))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                formatMediaDuration((block.duration * progress).toLong(), true),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = progress,
                onValueChange = onProgressChanged,
                modifier = Modifier.weight(1f),
                enabled = block.duration > 0,
            )
            Text(
                formatMediaDuration(block.duration, true),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("audio_block_duration"),
            )
        }
        val utterances = timedTranscript?.utterances.orEmpty().filter { it.text.isNotBlank() }
        if (utterances.isNotEmpty() || block.transcription.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("completed_audio_transcript"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (utterances.isEmpty()) {
                        Text(block.transcription, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        val currentPositionMs = (block.duration * progress).toLong()
                        utterances.forEachIndexed { index, utterance ->
                            val active = currentPositionMs >= utterance.startMs && currentPositionMs < utterance.endMs
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag("completed_audio_phrase_$index")
                                        .semantics { selected = active }
                                        .clickable(enabled = block.duration > 0, onClickLabel = "Play from this phrase") {
                                            onSeekTimestampClicked(utterance.startMs)
                                        },
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier.width(3.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(
                                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                )
                                Text(
                                    utterance.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CompletedPlaybackButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val corners by animateDpAsState(if (isPlaying) 16.dp else 22.dp, label = "CompletedPlaybackShape")
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        shape = RoundedCornerShape(corners),
    ) {
        MorphingPlayPauseIcon(isPlaying = isPlaying, size = 32.dp, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CompletedWaveform(
    amplitudes: List<Float>,
    progress: Float,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .testTag("completed_audio_waveform"),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .1f)),
        )
        AudioWaveformComponent(
            audioLevels = amplitudes,
            waveformColor = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            maxBars = 60,
            minHeight = 32.dp,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        )
    }
}
