package app.logdate.screenshots.flows.flow20_os_integration

import androidx.compose.runtime.Composable
import app.logdate.screenshots.common.OsIntegrationPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.shared.SharedScreenshotScene
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun S01_QuietSaveSettings() {
    ScreenshotTheme {
        SharedScreenshotScene(SharedScreenshotSceneId.QuietSaveSettings)
    }
}
