package app.logdate.wear.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8B1A1A)),
                contentAlignment = Alignment.Center,
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8B1A1A)),
                contentAlignment = Alignment.Center,
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1B5E20)),
                contentAlignment = Alignment.Center,
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                RecordingErrorContent(message = null)
            }
        }
    }
}
