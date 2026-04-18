@file:Suppress("DEPRECATION")

package app.logdate.wear.e2e

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the audio recording screen.
 *
 * Tests the content composable directly with controlled state since
 * AudioRecordingViewModel requires Application context and a bound service.
 */
@RunWith(AndroidJUnit4::class)
class AudioRecordingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Idle state
    // -----------------------------------------------------------------------

    @Test
    fun idleState_displaysRecordButton() {
        composeRule.setContent {
            MaterialTheme {
                AudioRecordingTestContent(
                    uiState = AudioRecordingUiState(),
                )
            }
        }

        composeRule.onNodeWithTag("record_button").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Recording state
    // -----------------------------------------------------------------------

    @Test
    fun recordingState_displaysWaveformAndTimer() {
        composeRule.setContent {
            MaterialTheme {
                AudioRecordingTestContent(
                    uiState =
                        AudioRecordingUiState(
                            isRecording = true,
                            durationMs = 5_000,
                            audioLevels = listOf(0.3f, 0.5f, 0.7f),
                        ),
                )
            }
        }

        composeRule.onNodeWithTag("audio_waveform").assertIsDisplayed()
        composeRule.onNodeWithTag("recording_timer").assertIsDisplayed()
    }

    @Test
    fun recordingState_displaysPauseButton() {
        composeRule.setContent {
            MaterialTheme {
                AudioRecordingTestContent(
                    uiState = AudioRecordingUiState(isRecording = true),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun recordingState_displaysCancelButton() {
        composeRule.setContent {
            MaterialTheme {
                AudioRecordingTestContent(
                    uiState = AudioRecordingUiState(isRecording = true),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Cancel").assertIsDisplayed()
    }

    @Test
    fun recordingState_pauseButtonTriggersCallback() {
        var paused = false
        composeRule.setContent {
            MaterialTheme {
                AudioRecordingTestContent(
                    uiState = AudioRecordingUiState(isRecording = true),
                    onPause = { paused = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Pause").performClick()
        assertTrue("Pause button should trigger callback", paused)
    }

    // -----------------------------------------------------------------------
    // Paused state
    // -----------------------------------------------------------------------

    @Test
    fun pausedState_displaysResumeButton() {
        composeRule.setContent {
            MaterialTheme {
                AudioRecordingTestContent(
                    uiState =
                        AudioRecordingUiState(
                            isRecording = true,
                            isPaused = true,
                        ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Resume").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Error state
    // -----------------------------------------------------------------------

    @Test
    fun errorState_displaysErrorMessage() {
        composeRule.setContent {
            MaterialTheme {
                AudioRecordingTestContent(
                    uiState =
                        AudioRecordingUiState(
                            errorMessage = "Not enough storage space for recording",
                        ),
                )
            }
        }

        composeRule
            .onNodeWithText("Not enough storage space for recording")
            .assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Record button interaction
    // -----------------------------------------------------------------------

    @Test
    fun recordButton_triggersStartWhenNotRecording() {
        var started = false
        composeRule.setContent {
            MaterialTheme {
                AudioRecordingTestContent(
                    uiState = AudioRecordingUiState(isRecording = false),
                    onToggleRecording = { started = true },
                )
            }
        }

        composeRule.onNodeWithTag("record_button").performClick()
        assertTrue("Record button should trigger start when not recording", started)
    }

    @Test
    fun recordButton_triggersStopWhenRecording() {
        var stopped = false
        composeRule.setContent {
            MaterialTheme {
                AudioRecordingTestContent(
                    uiState = AudioRecordingUiState(isRecording = true),
                    onToggleRecording = { stopped = true },
                )
            }
        }

        composeRule.onNodeWithTag("record_button").performClick()
        assertTrue("Record button should trigger stop when recording", stopped)
    }
}

/**
 * Stateless test wrapper for the audio recording screen content.
 *
 * Mirrors the layout from AudioRecordingScreen but accepts state directly,
 * avoiding the need for a bound WearAudioRecordingService.
 */
@Composable
private fun AudioRecordingTestContent(
    uiState: AudioRecordingUiState,
    onToggleRecording: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    ScreenScaffold(
        timeText = { TimeText() },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .padding(bottom = 4.dp)
                                .testTag("audio_waveform"),
                    )
                    RecordingTimer(
                        durationMs = uiState.durationMs,
                        isRecording = uiState.isRecording,
                        modifier =
                            Modifier
                                .padding(bottom = 8.dp)
                                .testTag("recording_timer"),
                    )
                }

                RecordButton(
                    isRecording = uiState.isRecording,
                    onClick = onToggleRecording,
                    modifier =
                        Modifier
                            .size(64.dp)
                            .testTag("record_button"),
                )

                if (uiState.isRecording) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Button(
                            onClick = if (uiState.isPaused) onResume else onPause,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (uiState.isPaused) {
                                        Icons.Filled.PlayArrow
                                    } else {
                                        Icons.Filled.Pause
                                    },
                                contentDescription = if (uiState.isPaused) "Resume" else "Pause",
                            )
                        }
                        Button(
                            onClick = onCancel,
                            colors =
                                ButtonDefaults.buttonColors(
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
