package app.logdate.wear.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.client.media.device.DefaultMediaDevices
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.ui.media.MediaDeviceSelector
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.wear.presentation.recording.ActiveRecordingContent
import app.logdate.wear.presentation.recording.ReadyContent
import app.logdate.wear.presentation.recording.RecordingErrorContent
import app.logdate.wear.presentation.recording.SavedContent
import app.logdate.wear.presentation.recording.SavingContent
import app.logdate.wear.presentation.recording.TooShortContent
import com.android.tools.screenshot.PreviewTest

class WearRecordingScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_RecordingReady() {
        MaterialTheme {
                WearRecordingPreviewScaffold(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    selection = microphoneSelection("Watch microphone"),
                ) {
                    ReadyContent()
                }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_RecordingActive() {
        MaterialTheme {
                WearRecordingPreviewScaffold(
                    backgroundColor = Color(0xFF8B1A1A),
                    selection = microphoneSelection("Bluetooth headset"),
                ) {
                    ActiveRecordingContent(
                    durationMs = 4_200,
                    audioLevels =
                        listOf(
                            0.3f,
                            0.5f,
                            0.7f,
                            0.4f,
                            0.8f,
                            0.6f,
                            0.9f,
                            0.5f,
                            0.3f,
                            0.7f,
                        ),
                )
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_RecordingActiveLong() {
        MaterialTheme {
                WearRecordingPreviewScaffold(
                    backgroundColor = Color(0xFF8B1A1A),
                    selection = microphoneSelection("USB microphone"),
                ) {
                ActiveRecordingContent(
                    durationMs = 58_000,
                    audioLevels = List(50) { it / 50f },
                )
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_RecordingSaving() {
        MaterialTheme {
                WearRecordingPreviewScaffold(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    selection = microphoneSelection("Watch microphone"),
                ) {
                SavingContent()
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S05_RecordingSaved() {
        MaterialTheme {
                WearRecordingPreviewScaffold(
                    backgroundColor = Color(0xFF1B5E20),
                    selection = microphoneSelection("Watch microphone"),
                ) {
                SavedContent(durationMs = 4_200)
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S06_RecordingTooShort() {
        MaterialTheme {
                WearRecordingPreviewScaffold(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    selection = microphoneSelection("Watch microphone"),
                ) {
                TooShortContent()
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S07_RecordingError() {
        MaterialTheme {
                WearRecordingPreviewScaffold(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    selection = microphoneSelection("System", controllable = false),
                ) {
                RecordingErrorContent(message = "Microphone unavailable")
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S08_RecordingErrorNull() {
        MaterialTheme {
                WearRecordingPreviewScaffold(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    selection = microphoneSelection("System", controllable = false),
                ) {
                RecordingErrorContent(message = null)
            }
        }
    }
}

@Composable
private fun WearRecordingPreviewScaffold(
    backgroundColor: Color,
    selection: MediaDeviceSelectionUiState,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        content()
        androidx.compose.foundation.layout.Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Mic: ${selection.selectedDevice?.label ?: "System"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            MediaDeviceSelector(
                selection = selection,
                onDeviceSelected = {},
                label = "Microphone",
            )
        }
    }
}

private fun microphoneSelection(
    selectedLabel: String,
    controllable: Boolean = true,
): MediaDeviceSelectionUiState =
    MediaDeviceSelectionUiState(
        kind = MediaDeviceKind.AUDIO_INPUT,
        devices =
            listOf(
                DefaultMediaDevices.systemMicrophone.copy(id = "watch-mic", label = "Watch microphone"),
                MediaDeviceUiState(
                    id = "usb-mic",
                    label = "USB microphone",
                    kind = MediaDeviceKind.AUDIO_INPUT,
                    category = MediaDeviceCategory.USB,
                    isExternal = true,
                ),
                MediaDeviceUiState(
                    id = "bluetooth-mic",
                    label = "Bluetooth headset",
                    kind = MediaDeviceKind.AUDIO_INPUT,
                    category = MediaDeviceCategory.BLUETOOTH,
                    isExternal = true,
                ),
            ),
        selectedDeviceId =
            when (selectedLabel) {
                "USB microphone" -> "usb-mic"
                "Bluetooth headset" -> "bluetooth-mic"
                else -> "watch-mic"
            },
        isSelectionControllable = controllable,
        routeControlMessage =
            if (controllable) null else "Microphone selection is currently controlled by the system.",
    )
