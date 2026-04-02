package app.logdate.screenshots.flows.flow01_onboarding

import androidx.compose.runtime.Composable
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.client.media.MediaObject
import app.logdate.feature.onboarding.ui.CloudAccountSetupContent
import app.logdate.feature.onboarding.ui.CloudSetupOption
import app.logdate.feature.onboarding.ui.MemorySelectionScreen
import app.logdate.feature.onboarding.ui.MemorySelectionUiState
import app.logdate.feature.onboarding.ui.OnboardingDayBoundariesContent
import app.logdate.feature.onboarding.ui.OnboardingNotificationsContent
import app.logdate.feature.onboarding.ui.OnboardingRecommendationsContent
import app.logdate.screenshots.common.LargeScreenAuditPreviewMatrix
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Clock
import kotlin.time.Duration

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_MemorySelectionEmpty() {
    ScreenshotTheme {
        MemorySelectionScreen(
            uiState = MemorySelectionUiState(isLoading = false, hasMoreMemories = false),
            onBack = {},
            onContinue = {},
            onToggleMemorySelection = {},
            onLoadMoreMemories = {},
            onRefreshMemories = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_MemorySelectionError() {
    ScreenshotTheme {
        MemorySelectionScreen(
            uiState =
                MemorySelectionUiState(
                    isLoading = false,
                    hasMoreMemories = false,
                    loadFailed = true,
                ),
            onBack = {},
            onContinue = {},
            onToggleMemorySelection = {},
            onLoadMoreMemories = {},
            onRefreshMemories = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_MemorySelectionLoading() {
    ScreenshotTheme {
        MemorySelectionScreen(
            uiState = MemorySelectionUiState(isLoading = true),
            onBack = {},
            onContinue = {},
            onToggleMemorySelection = {},
            onLoadMoreMemories = {},
            onRefreshMemories = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_RecommendationsSaving() {
    ScreenshotTheme {
        OnboardingRecommendationsContent(
            onBack = {},
            onKeepOn = {},
            onTurnOff = {},
            isSaving = true,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_DayBoundariesPermissionsNeeded() {
    ScreenshotTheme {
        OnboardingDayBoundariesContent(
            healthConnectStatus = HealthConnectStatus.PERMISSIONS_NEEDED,
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_DayBoundariesChecking() {
    ScreenshotTheme {
        OnboardingDayBoundariesContent(
            healthConnectStatus = HealthConnectStatus.CHECKING,
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_NotificationsDecisionHandled() {
    ScreenshotTheme {
        OnboardingNotificationsContent(
            onBack = {},
            onPrimaryAction = {},
            onSkip = {},
            recommendationsEnabled = false,
            hasDecision = true,
            hasPermission = false,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_CloudAccountSelectedSignIn() {
    ScreenshotTheme {
        CloudAccountSetupContent(
            useCompactLayout = true,
            selectedOption = CloudSetupOption.SIGN_IN,
            onBack = {},
            onOptionSelected = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun S09_CloudAccountAdaptiveLargeScreen() {
    ScreenshotTheme {
        CloudAccountSetupContent(
            useCompactLayout = false,
            selectedOption = CloudSetupOption.CREATE_ACCOUNT,
            onBack = {},
            onOptionSelected = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun S10_MemorySelectionAdaptiveLargeScreen() {
    ScreenshotTheme {
        val sampleMemories =
            (1..12).map { index ->
                if (index % 4 == 0) {
                    MediaObject.Video(
                        uri = "sample$index",
                        size = 2048,
                        name = "VID_$index.mp4",
                        timestamp = Clock.System.now(),
                        duration = Duration.parse("45s"),
                    )
                } else {
                    MediaObject.Image(
                        uri = "sample$index",
                        size = 1024,
                        name = "IMG_$index.jpg",
                        timestamp = Clock.System.now(),
                    )
                }
            }
        MemorySelectionScreen(
            uiState =
                MemorySelectionUiState(
                    allMemories = sampleMemories,
                    aiCuratedMemories = sampleMemories.take(4),
                    selectedMemoryIds = setOf("sample1", "sample2", "sample7"),
                    isLoading = false,
                    hasMoreMemories = false,
                ),
            onBack = {},
            onContinue = {},
            onToggleMemorySelection = {},
            onLoadMoreMemories = {},
            onRefreshMemories = {},
        )
    }
}
