package app.logdate.screenshots.components.settings_account

import androidx.compose.runtime.Composable
import app.logdate.screenshots.common.OsIntegrationPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.shared.SharedScreenshotScene
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun JournalStoragePaused() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.JournalStoragePaused) }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun JournalStorageSealed() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.JournalStorageSealed) }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun SyncUpdateRequired() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.SyncUpdateRequired) }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun StoragePreviews() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.StoragePreviews) }
}
