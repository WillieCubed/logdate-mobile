package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.wear.presentation.home.WearHomeContent
import app.logdate.wear.presentation.home.WearHomeUiState
import com.android.tools.screenshot.PreviewTest

class WearHomeScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_HomeEmpty() {
        MaterialTheme {
            WearHomeContent(
                homeState =
                    WearHomeUiState(
                        greeting = "Good morning",
                        entryCount = 0,
                        entryCountLabel = "No entries yet",
                    ),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_HomeWithEntries() {
        MaterialTheme {
            WearHomeContent(
                homeState =
                    WearHomeUiState(
                        greeting = "Good afternoon",
                        entryCount = 5,
                        entryCountLabel = "5 entries today",
                    ),
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_HomeSingleEntry() {
        MaterialTheme {
            WearHomeContent(
                homeState =
                    WearHomeUiState(
                        greeting = "Good evening",
                        entryCount = 1,
                        entryCountLabel = "1 entry today",
                    ),
            )
        }
    }
}
