package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.logdate.client.media.audio.transcription.TimedTranscript
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
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
    availableHeight: Dp = Dp.Infinity,
    cornerRadius: Dp = 32.dp,
    trailingActionInset: Dp = 0.dp,
) {
    val sharedScope = LocalSharedTransitionScope.current
    val content: @Composable SharedTransitionScope.() -> Unit = {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val transcriptHeightLimit = (maxWidth * .75f * LocalDensity.current.fontScale).coerceAtMost(400.dp)
            val blockHeightLimit = minOf(maxHeight, availableHeight, transcriptHeightLimit + 280.dp).coerceAtLeast(0.dp)
            AnimatedContent(targetState = isExpanded, label = "CompletedAudioExpansion") { expanded ->
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
                        heightLimit = blockHeightLimit,
                        transcriptHeightLimit = transcriptHeightLimit,
                        trailingActionInset = trailingActionInset,
                        transcriptRadius = cornerRadius,
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
                        trailingActionInset,
                        cornerRadius,
                    )
                }
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
    trailingActionInset: Dp,
    transcriptRadius: Dp,
) {
    val transcriptText = timedTranscript?.plainText?.takeIf(String::isNotBlank) ?: block.transcription
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (transcriptText.isNotBlank()) {
            Surface(shape = RoundedCornerShape(transcriptRadius), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(
                    text = transcriptText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.fillMaxWidth().padding(
                            start = 12.dp,
                            top = 12.dp,
                            end = 12.dp + trailingActionInset,
                            bottom = 12.dp,
                        ),
                )
            }
        }
        Box(Modifier.padding(12.dp)) {
            CompletedAudioTransport(
                block.duration,
                isPlaying,
                progress = 0f,
                amplitudes = waveformAmplitudes,
                onPlayPauseClicked = onPlayPauseClicked,
                onProgressChanged = null,
                playbackModifier = playbackModifier,
                waveformModifier = waveformModifier,
                compact = true,
                trailingActionInset = if (transcriptText.isBlank()) trailingActionInset else 0.dp,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    heightLimit: Dp,
    transcriptHeightLimit: Dp,
    trailingActionInset: Dp,
    transcriptRadius: Dp,
) {
    val utterances = timedTranscript?.utterances.orEmpty().filter { it.text.isNotBlank() }
    val hasTranscript = utterances.isNotEmpty() || block.transcription.isNotBlank()
    val hasOutputChoice =
        outputSelection
            ?.devices
            ?.filter { it.isAvailable }
            ?.distinctBy { it.groupKey }
            ?.size
            ?.let { it > 1 } == true
    val density = LocalDensity.current
    val transcriptReserve =
        if (hasTranscript) {
            with(density) {
                MaterialTheme.typography.bodyLarge.lineHeight
                    .toDp()
            } + 32.dp
        } else {
            0.dp
        }
    val chromeHeight = (if (showDeleteAction) 48.dp else 0.dp) + (if (hasOutputChoice) 64.dp else 0.dp)
    val minimumTransportHeight =
        with(density) {
            (
                MaterialTheme.typography.labelSmall.lineHeight
                    .takeIf { it.isSp } ?: 16.sp
            ).toDp()
        } + 4.dp + IconButtonDefaults.mediumContainerSize().height
    val mustScroll = heightLimit < transcriptReserve + chromeHeight + minimumTransportHeight + 24.dp
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = heightLimit)
                .then(if (mustScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
    ) {
        if (showDeleteAction) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (block.caption.isNotBlank()) {
                    Text(block.caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f))
                }
                IconButton(onClick = onDeleteClicked) {
                    Icon(PlatformIcons.delete(), stringResource(UiRes.string.common_delete))
                }
            }
        }
        if (hasTranscript) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (mustScroll) Modifier else Modifier.weight(1f, fill = false))
                        .heightIn(min = 132.dp, max = transcriptHeightLimit.coerceAtLeast(132.dp))
                        .testTag("completed_audio_transcript"),
                shape = RoundedCornerShape(transcriptRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier =
                        Modifier.verticalScroll(rememberScrollState()).padding(
                            start = 16.dp,
                            top = 16.dp,
                            end =
                                16.dp + trailingActionInset,
                            bottom = 16.dp,
                        ),
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
                                        .clickable(
                                            enabled = block.duration > 0,
                                            onClickLabel = "Play from this phrase",
                                        ) { onSeekTimestampClicked(utterance.startMs) },
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
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
        if (hasOutputChoice) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                MediaDeviceSelector(outputSelection, onOutputDeviceSelected, label = "Audio output")
            }
        }
        Box(Modifier.padding(12.dp)) {
            CompletedAudioTransport(
                block.duration,
                isPlaying,
                progress,
                waveformAmplitudes,
                onPlayPauseClicked,
                onProgressChanged,
                playbackModifier,
                waveformModifier,
                trailingActionInset = if (hasTranscript) 0.dp else trailingActionInset,
            )
        }
    }
}
