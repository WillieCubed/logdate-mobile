package app.logdate.screenshots.flows.flow01_onboarding

import androidx.compose.runtime.Composable
import app.logdate.screenshots.common.LargeScreenAuditPreviewMatrix
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.shared.SharedScreenshotScene
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_MemorySelectionEmpty() = SharedOnboardingExtendedScene(SharedScreenshotSceneId.MemorySelectionEmpty)

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_MemorySelectionError() = SharedOnboardingExtendedScene(SharedScreenshotSceneId.MemorySelectionError)

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_MemorySelectionLoading() = SharedOnboardingExtendedScene(SharedScreenshotSceneId.MemorySelectionLoading)

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_RecommendationsSaving() = SharedOnboardingExtendedScene(SharedScreenshotSceneId.RecommendationsSaving)

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_DayBoundariesPermissionsNeeded() =
    SharedOnboardingExtendedScene(SharedScreenshotSceneId.DayBoundariesPermissionsNeeded)

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_DayBoundariesChecking() = SharedOnboardingExtendedScene(SharedScreenshotSceneId.DayBoundariesChecking)

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_NotificationsDecisionHandled() =
    SharedOnboardingExtendedScene(SharedScreenshotSceneId.NotificationsDecisionHandled)

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_CloudAccountSelectedSignIn() =
    SharedOnboardingExtendedScene(SharedScreenshotSceneId.CloudAccountSelectedSignIn)

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun S09_CloudAccountAdaptiveLargeScreen() =
    SharedOnboardingExtendedScene(SharedScreenshotSceneId.CloudAccountAdaptiveLargeScreen)

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun S10_MemorySelectionAdaptiveLargeScreen() =
    SharedOnboardingExtendedScene(SharedScreenshotSceneId.MemorySelectionAdaptiveLargeScreen)

@Composable
private fun SharedOnboardingExtendedScene(
    sceneId: SharedScreenshotSceneId,
) {
    ScreenshotTheme {
        SharedScreenshotScene(sceneId)
    }
}
