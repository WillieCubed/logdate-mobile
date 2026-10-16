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
fun NativeBackupSignedOut() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.NativeBackupSignedOut) }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun NativeBackupReady() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.NativeBackupReady) }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun NativeBackupQuota() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.NativeBackupQuota) }
}
