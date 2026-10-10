package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.IconTooltip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.finish
import logdate.client.feature.editor.generated.resources.listening
import logdate.client.feature.editor.generated.resources.recorder_bubble_no_transcript
import logdate.client.feature.editor.generated.resources.recorder_bubble_open_app
import logdate.client.feature.editor.generated.resources.recorder_bubble_pause
import logdate.client.feature.editor.generated.resources.recorder_bubble_paused
import logdate.client.feature.editor.generated.resources.recorder_bubble_recording
import logdate.client.feature.editor.generated.resources.recorder_bubble_resume
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/** Below this height the recorder bubble drops the transcript and shows only the transport. */
private val TranscriptMinHeight = 320.dp

/** The recorder's widest layout; a wider window centers it, so the waveform spans it edge to edge. */
private val RecorderMaxWidth = 480.dp

/**
 * What the floating recorder shows: the running recording's elapsed time, recent input levels,
 * whether it is paused, and the live transcript when one is available.
 */
data class RecorderBubbleState(
    val elapsed: Duration,
    val levels: List<Float>,
    val paused: Boolean,
    val transcript: String?,
)

/** How many recent input levels the bubble's waveform draws. */
const val RECORDER_BUBBLE_LEVEL_HISTORY = 64

/**
 * Combines a recorder's live streams into [RecorderBubbleState]s. Each stream starts from a neutral
 * value, so the bubble draws at once instead of waiting for every stream to emit; the waveform keeps
 * the latest [levelHistory] input levels.
 */
fun recorderBubbleStates(
    elapsed: Flow<Duration>,
    levels: Flow<Float>,
    paused: Flow<Boolean>,
    transcript: Flow<String?>,
    initiallyPaused: Boolean,
    levelHistory: Int = RECORDER_BUBBLE_LEVEL_HISTORY,
): Flow<RecorderBubbleState> =
    combine(
        elapsed.onStart { emit(Duration.ZERO) },
        levels.runningFold(emptyList<Float>()) { history, level -> (history + level).takeLast(levelHistory) },
        paused.onStart { emit(initiallyPaused) },
        transcript.onStart { emit(null) },
    ) { elapsedNow, levelHistoryNow, pausedNow, transcriptNow ->
        RecorderBubbleState(elapsed = elapsedNow, levels = levelHistoryNow, paused = pausedNow, transcript = transcriptNow)
    }

/**
 * The compact recorder shown in a floating bubble while a voice entry records in the background.
 *
 * It fills whatever window the bubble gives it. When the bubble is tall enough the live transcript
 * takes the free space; in a short bubble only the status, waveform, time, and controls remain.
 * Finish stops the recording and saves it to the entry that started it, exactly like the
 * notification's Stop action.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun RecorderBubbleContent(
    state: RecorderBubbleState,
    onPauseToggle: () -> Unit,
    onFinish: () -> Unit,
    onOpenApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .testTag("recorder_bubble"),
    ) {
        val showTranscript = maxHeight >= TranscriptMinHeight
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = RecorderMaxWidth)
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecorderBubbleHeader(paused = state.paused, onOpenApp = onOpenApp)
            if (showTranscript) {
                RecorderBubbleTranscript(
                    transcript = state.transcript,
                    paused = state.paused,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            RecordingWaveform(
                levels = state.levels,
                isRecording = !state.paused,
                focused = false,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )
            Text(
                text = formatDuration(state.elapsed),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().testTag("recorder_bubble_elapsed"),
            )
            RecorderBubbleControls(paused = state.paused, onPauseToggle = onPauseToggle, onFinish = onFinish)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RecorderBubbleHeader(
    paused: Boolean,
    onOpenApp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(
                    color = if (paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                    shape = CircleShape,
                ),
        )
        Text(
            text = stringResource(if (paused) Res.string.recorder_bubble_paused else Res.string.recorder_bubble_recording),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        val openApp = stringResource(Res.string.recorder_bubble_open_app)
        IconTooltip(label = openApp) {
            IconButton(onClick = onOpenApp) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = openApp)
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RecorderBubbleTranscript(
    transcript: String?,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(transcript) { scrollState.animateScrollTo(scrollState.maxValue) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
            val text = transcript?.takeIf { it.isNotBlank() }
            Text(
                text = text ?: stringResource(if (paused) Res.string.recorder_bubble_no_transcript else Res.string.listening),
                // The transcript is in whatever language was spoken, not the app's layout direction.
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
                color =
                    if (text != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RecorderBubbleControls(
    paused: Boolean,
    onPauseToggle: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val pauseLabel = stringResource(if (paused) Res.string.recorder_bubble_resume else Res.string.recorder_bubble_pause)
        IconTooltip(label = pauseLabel) {
            FilledTonalIconButton(onClick = onPauseToggle, modifier = Modifier.size(56.dp)) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = pauseLabel)
            }
        }
        Button(onClick = onFinish, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(Res.string.finish), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
