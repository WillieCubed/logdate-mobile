package app.logdate.wear.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.wear.presentation.audio.AudioRecordingUiState
import app.logdate.wear.presentation.audio.components.AudioWaveform
import app.logdate.wear.presentation.audio.components.RecordButton
import app.logdate.wear.presentation.audio.components.RecordingTimer
import com.android.tools.screenshot.PreviewTest

class AudioRecordingScreenshots {

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_AudioRecordingIdle() {
        MaterialTheme {
            AudioRecordingPreviewContent(
                uiState = AudioRecordingUiState(),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_AudioRecordingActive() {
        MaterialTheme {
            AudioRecordingPreviewContent(
                uiState = AudioRecordingUiState(
                    isRecording = true,
                    durationMs = 34_000,
                    audioLevels = listOf(
                        0.3f, 0.5f, 0.7f, 0.4f, 0.8f,
                        0.6f, 0.9f, 0.5f, 0.3f, 0.7f,
                    ),
                ),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_AudioRecordingPaused() {
        MaterialTheme {
            AudioRecordingPreviewContent(
                uiState = AudioRecordingUiState(
                    isRecording = true,
                    isPaused = true,
                    durationMs = 34_000,
                    audioLevels = listOf(0.3f, 0.5f, 0.7f),
                ),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_AudioRecordingError() {
        MaterialTheme {
            AudioRecordingPreviewContent(
                uiState = AudioRecordingUiState(
                    errorMessage = "Not enough storage space for recording",
                ),
            )
        }
    }
}

/**
 * Stateless preview wrapper for the audio recording screen.
 *
 * Mirrors the layout from AudioRecordingScreen but accepts state directly,
 * avoiding the need for a ViewModel or bound service.
 */
@Composable
private fun AudioRecordingPreviewContent(
    uiState: AudioRecordingUiState,
) {
    ScreenScaffold(
        timeText = { TimeText() },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                if (uiState.isRecording) {
                    AudioWaveform(
                        audioLevels = uiState.audioLevels,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(bottom = 4.dp),
                    )
                    RecordingTimer(
                        durationMs = uiState.durationMs,
                        isRecording = uiState.isRecording,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                RecordButton(
                    isRecording = uiState.isRecording,
                    onClick = {},
                    modifier = Modifier.size(64.dp),
                )

                if (uiState.isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (uiState.isPaused) {
                                    Icons.Filled.PlayArrow
                                } else {
                                    Icons.Filled.Pause
                                },
                                contentDescription = if (uiState.isPaused) "Resume" else "Pause",
                            )
                        }
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel",
                            )
                        }
                    }
                }
            }
        }
    }
}
