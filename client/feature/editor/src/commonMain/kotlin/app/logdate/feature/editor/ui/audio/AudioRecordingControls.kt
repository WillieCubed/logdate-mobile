package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.logdate.feature.editor.ui.editor.RecordingState
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.platform.rememberLogDateHaptics
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.record
import logdate.client.feature.editor.generated.resources.start_recording
import logdate.client.feature.editor.generated.resources.stop
import logdate.client.feature.editor.generated.resources.stop_recording
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * Audio recording controls that display current recording state, buttons,
 * and visualizations.
 *
 * @param recordingState Current state of recording
 * @param audioLevels Audio level data for visualization
 * @param recordingDuration Current duration of the recording
 * @param onStartRecording Callback when recording should start
 * @param onStopRecording Callback when recording should stop
 * @param modifier Modifier for the root component
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun AudioRecordingControls(
    recordingState: RecordingState,
    audioLevels: List<Float>,
    recordingDuration: Duration,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberLogDateHaptics()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Recording state indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text =
                    if (recordingState == RecordingState.RECORDING) {
                        "Recording in progress"
                    } else {
                        "Ready to record"
                    },
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (recordingState == RecordingState.RECORDING) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )

            if (recordingState == RecordingState.RECORDING) {
                // Blinking recording indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                    )
                    Text(
                        text = recordingDuration.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        // Waveform area — placeholder before recording starts, live waveform during/after
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (recordingState == RecordingState.RECORDING) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (audioLevels.isEmpty() && recordingState != RecordingState.RECORDING) {
                // Placeholder: no data yet
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = PlatformIcons.mic(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = "Tap record to begin",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            } else {
                AudioWaveformComponent(
                    audioLevels = audioLevels,
                    isRecording = recordingState == RecordingState.RECORDING,
                    waveformColor =
                        if (recordingState == RecordingState.RECORDING) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    strokeWidth = 3.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Recording controls
        if (recordingState == RecordingState.RECORDING) {
            // Stop button
            Button(
                onClick = {
                    haptics.recordingFinished()
                    onStopRecording()
                },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth(0.8f)
                        .testTag("audio_record_stop_button"),
            ) {
                Icon(
                    painter = PlatformIcons.stop(),
                    contentDescription = stringResource(Res.string.stop_recording),
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(Res.string.stop),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        } else {
            // Start button
            Button(
                onClick = {
                    haptics.recordingStarted()
                    onStartRecording()
                },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth(0.8f)
                        .testTag("audio_record_start_button"),
            ) {
                Icon(
                    painter = PlatformIcons.mic(),
                    contentDescription = stringResource(Res.string.start_recording),
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(Res.string.record),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}
