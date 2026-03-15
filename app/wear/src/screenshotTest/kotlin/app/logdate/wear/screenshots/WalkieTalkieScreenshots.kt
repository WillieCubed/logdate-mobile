package app.logdate.wear.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.presentation.walkietalkie.ReadyContent
import app.logdate.wear.presentation.walkietalkie.RecordingContent
import app.logdate.wear.presentation.walkietalkie.SavingContent
import app.logdate.wear.presentation.walkietalkie.TooShortContent
import app.logdate.wear.presentation.walkietalkie.WalkieTalkieErrorContent
import app.logdate.wear.presentation.walkietalkie.WalkieTalkieSavedContent
import com.android.tools.screenshot.PreviewTest

class WalkieTalkieScreenshots {

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_WalkieTalkieReady() {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                ReadyContent(onTouchDown = {}, onTouchUp = {})
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_WalkieTalkieRecording() {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF8B1A1A)),
                contentAlignment = Alignment.Center,
            ) {
                RecordingContent(
                    durationMs = 4_200,
                    audioLevels = listOf(
                        0.3f, 0.5f, 0.7f, 0.4f, 0.8f,
                        0.6f, 0.9f, 0.5f, 0.3f, 0.7f,
                    ),
                    onTouchUp = {},
                )
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_WalkieTalkieRecordingLong() {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF8B1A1A)),
                contentAlignment = Alignment.Center,
            ) {
                RecordingContent(
                    durationMs = 58_000,
                    audioLevels = List(50) { it / 50f },
                    onTouchUp = {},
                )
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_WalkieTalkieSaving() {
        MaterialTheme {
            Box(
                modifier = Modifier
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
    fun S05_WalkieTalkieSaved() {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1B5E20)),
                contentAlignment = Alignment.Center,
            ) {
                WalkieTalkieSavedContent(durationMs = 4_200)
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S06_WalkieTalkieTooShort() {
        MaterialTheme {
            Box(
                modifier = Modifier
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
    fun S07_WalkieTalkieError() {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                WalkieTalkieErrorContent(message = "Microphone unavailable")
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S08_WalkieTalkieErrorNull() {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                WalkieTalkieErrorContent(message = null)
            }
        }
    }
}
