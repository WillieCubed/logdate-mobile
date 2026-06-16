@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.media.MediaDeviceSelector
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.finish
import logdate.client.feature.editor.generated.resources.listening
import logdate.client.feature.editor.generated.resources.restart
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * A component that displays the active recording interface with controls.
 *
 * Layout (top to bottom):
 * 1. Scrollable transcription text area (takes remaining space)
 * 2. Recording info bar (red dot + timer + compact waveform)
 * 3. Control buttons (Restart, Pause, Finish)
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun ActiveRecordingDisplay(
    audioLevels: List<Float>,
    recordingDuration: Duration,
    onRestart: () -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    inputSelection: MediaDeviceSelectionUiState? = null,
    onInputSelected: (String) -> Unit = {},
    transcriptionText: String? = null,
    transcriptionIsFinal: Boolean = false,
    transcriptionIsRefining: Boolean = false,
    isPaused: Boolean = false,
) {
    FoldableTabletopLayout(
        modifier = modifier,
        minPaneHeight = 180.dp,
        topPane = {
            ActiveRecordingTranscriptPane(
                transcriptionText = transcriptionText,
                transcriptionIsFinal = transcriptionIsFinal,
                transcriptionIsRefining = transcriptionIsRefining,
                isPaused = isPaused,
                inputSelection = inputSelection,
                onInputSelected = onInputSelected,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        },
        bottomPane = {
            ActiveRecordingTransportPane(
                audioLevels = audioLevels,
                recordingDuration = recordingDuration,
                onRestart = onRestart,
                onPause = onPause,
                onFinish = onFinish,
                isPaused = isPaused,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
            )
        },
        standardContent = {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                ActiveRecordingTranscriptPane(
                    transcriptionText = transcriptionText,
                    transcriptionIsFinal = transcriptionIsFinal,
                    transcriptionIsRefining = transcriptionIsRefining,
                    isPaused = isPaused,
                    inputSelection = inputSelection,
                    onInputSelected = onInputSelected,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                )
                ActiveRecordingTransportPane(
                    audioLevels = audioLevels,
                    recordingDuration = recordingDuration,
                    onRestart = onRestart,
                    onPause = onPause,
                    onFinish = onFinish,
                    isPaused = isPaused,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun ActiveRecordingTranscriptPane(
    transcriptionText: String?,
    transcriptionIsFinal: Boolean,
    transcriptionIsRefining: Boolean,
    isPaused: Boolean,
    inputSelection: MediaDeviceSelectionUiState?,
    onInputSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(transcriptionText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RecordingTranscriptStatus(
            isPaused = isPaused,
            isFinal = transcriptionIsFinal,
            isRefining = transcriptionIsRefining,
            hasTranscript = !transcriptionText.isNullOrBlank(),
        )

        inputSelection?.let { selection ->
            MediaDeviceSelector(
                selection = selection,
                onDeviceSelected = onInputSelected,
                label = "Microphone",
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 160.dp)
                    .verticalScroll(scrollState),
        ) {
            val text = transcriptionText.takeUnless { it.isNullOrBlank() }
            if (text == null) {
                Text(
                    text = stringResource(Res.string.listening),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            } else {
                LiveTranscriptText(
                    text = text,
                    isRefining = transcriptionIsRefining,
                )
            }
        }
    }
}

@Composable
private fun ActiveRecordingTransportPane(
    audioLevels: List<Float>,
    recordingDuration: Duration,
    onRestart: () -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!isPaused) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                    )
                }
                Text(
                    text = formatDuration(recordingDuration),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            AudioWaveformComponent(
                audioLevels = audioLevels,
                isRecording = !isPaused,
                waveformColor = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                minHeight = 48.dp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(Res.string.restart))
                }

                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(if (isPaused) "Resume" else "Pause")
                }

                Button(
                    onClick = onFinish,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Text(stringResource(Res.string.finish))
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RecordingTranscriptStatus(
    isPaused: Boolean,
    isFinal: Boolean,
    isRefining: Boolean,
    hasTranscript: Boolean,
) {
    val label =
        when {
            isPaused -> "Paused"
            isRefining -> "Improving transcript"
            isFinal -> "Transcript ready"
            hasTranscript -> "Live on-device"
            else -> "Listening"
        }
    val indicatorColor =
        when {
            isPaused -> MaterialTheme.colorScheme.outline
            isFinal -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun LiveTranscriptText(
    text: String,
    isRefining: Boolean,
) {
    val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        paragraphs.forEachIndexed { index, paragraph ->
            val isLatest = index == paragraphs.lastIndex
            Text(
                text = paragraph,
                style =
                    if (isLatest && !isRefining) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                color =
                    if (isLatest && isRefining) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

/**
 * Helper function to format the duration as MM:SS.S
 */
private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (duration.inWholeMilliseconds % 1000) / 100

    val minutesStr = "$minutes"
    val secondsStr = if (seconds < 10) "0$seconds" else "$seconds"

    return "$minutesStr:$secondsStr.$tenths"
}
