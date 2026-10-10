package app.logdate.screenshots.flows.flow20_os_integration

import androidx.compose.runtime.Composable
import app.logdate.screenshots.common.OsIntegrationPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.shared.SharedScreenshotScene
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import com.android.tools.screenshot.PreviewTest

/**
 * Developer tools, where each Android OS integration surface can be turned on before it ships to
 * everyone. Quick Settings tiles and Live Updates are shown on.
 */
@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun S01_FeaturePreviews() {
    ScreenshotTheme {
        SharedScreenshotScene(SharedScreenshotSceneId.FeaturePreviews)
    }
}
