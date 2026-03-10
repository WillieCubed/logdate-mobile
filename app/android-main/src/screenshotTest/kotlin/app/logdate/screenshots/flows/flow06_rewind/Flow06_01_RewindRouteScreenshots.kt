package app.logdate.screenshots.flows.flow06_rewind

import androidx.compose.runtime.Composable
import app.logdate.feature.rewind.ui.BasicTextRewindPanelUiState
import app.logdate.feature.rewind.ui.ImageRewindPanelUiState
import app.logdate.feature.rewind.ui.RewindDetailUiState
import app.logdate.feature.rewind.ui.RewindPanelBackgroundSpec
import app.logdate.feature.rewind.ui.SubtitledRewindPanelUiState
import app.logdate.feature.rewind.ui.detail.RewindDetailScreenContent
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.uuid.Uuid

private val rewindId = Uuid.parse("00000000-0000-0000-0000-000000000095")

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_RewindDetailLoading() {
    ScreenshotTheme {
        RewindDetailScreenContent(
            uiState = RewindDetailUiState.Loading,
            onExitRewind = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_RewindDetailError() {
    ScreenshotTheme {
        RewindDetailScreenContent(
            uiState = RewindDetailUiState.Error.LoadingFailed,
            onExitRewind = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_RewindDetailStory() {
    ScreenshotTheme {
        RewindDetailScreenContent(
            uiState =
                RewindDetailUiState.Success(
                    panels =
                        listOf(
                            SubtitledRewindPanelUiState(
                                title = "A Week of Intentional Progress",
                                subtitle = "February 17 - 23, 2025",
                            ),
                            ImageRewindPanelUiState(
                                sourceId = rewindId,
                                timestamp = ScreenshotTestData.baseInstant,
                                imageUri = "android.resource://co.reasonabletech.logdate/mipmap/ic_launcher",
                                caption = "Wrapped the route harness late into the evening.",
                                dateFormatted = "Feb 20, 2025",
                            ),
                            BasicTextRewindPanelUiState(
                                text = "The work tightened up once the route inventory was real instead of inferred.",
                                background = RewindPanelBackgroundSpec(color = 0xFF1A1A1A),
                            ),
                        ),
                ),
            onExitRewind = {},
        )
    }
}
