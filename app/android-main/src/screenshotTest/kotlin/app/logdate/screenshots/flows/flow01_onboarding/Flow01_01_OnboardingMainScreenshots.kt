package app.logdate.screenshots.flows.flow01_onboarding

import androidx.compose.runtime.Composable
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.client.media.MediaObject
import app.logdate.feature.onboarding.ui.CloudAccountSetupContent
import app.logdate.feature.onboarding.ui.CloudSetupOption
import app.logdate.feature.onboarding.ui.MemoriesImportInfoScreen
import app.logdate.feature.onboarding.ui.MemorySelectionScreen
import app.logdate.feature.onboarding.ui.MemorySelectionUiState
import app.logdate.feature.onboarding.ui.OnboardingBirthdayContent
import app.logdate.feature.onboarding.ui.OnboardingCompletionContent
import app.logdate.feature.onboarding.ui.OnboardingDayBoundariesContent
import app.logdate.feature.onboarding.ui.OnboardingLocationContent
import app.logdate.feature.onboarding.ui.OnboardingNotificationsContent
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingRecommendationsContent
import app.logdate.feature.onboarding.ui.OnboardingStartScreenContent
import app.logdate.feature.onboarding.ui.PersonalIntroContent
import app.logdate.feature.onboarding.ui.PersonalIntroStep
import app.logdate.feature.onboarding.ui.PersonalIntroUiState
import app.logdate.feature.onboarding.ui.WelcomeBackScreenContent
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Clock
import kotlin.time.Duration

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_OnboardingStartSplash() {
    ScreenshotTheme {
        OnboardingStartScreenContent(
            showLanding = false,
            onGetStarted = {},
            onStartFromBackup = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_OnboardingStartLanding() {
    ScreenshotTheme {
        OnboardingStartScreenContent(
            showLanding = true,
            onGetStarted = {},
            onStartFromBackup = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_PersonalIntroNameStep() {
    ScreenshotTheme {
        PersonalIntroContent(
            uiState =
                PersonalIntroUiState(
                    name = "Alex",
                    currentStep = PersonalIntroStep.Name,
                ),
            onNameChanged = {},
            onBioChanged = {},
            onProceedToBio = {},
            onGoBackToName = {},
            onProcessWithLlm = {},
            onBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_PersonalIntroBioStep() {
    ScreenshotTheme {
        PersonalIntroContent(
            uiState =
                PersonalIntroUiState(
                    name = "Alex",
                    bio = "I keep a private timeline of the people, places, and moments that matter.",
                    currentStep = PersonalIntroStep.Bio,
                ),
            onNameChanged = {},
            onBioChanged = {},
            onProceedToBio = {},
            onGoBackToName = {},
            onProcessWithLlm = {},
            onBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_OnboardingOverview() {
    ScreenshotTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {})
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_MemoriesImportInfo() {
    ScreenshotTheme {
        MemoriesImportInfoScreen(onBack = {}, onContinue = {})
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_MemorySelectionPopulated() {
    ScreenshotTheme {
        val sampleMemories = previewMemories()
        MemorySelectionScreen(
            uiState =
                MemorySelectionUiState(
                    allMemories = sampleMemories,
                    aiCuratedMemories = sampleMemories.take(6),
                    selectedMemoryIds = setOf("sample1", "sample5"),
                    isLoading = false,
                    hasMoreMemories = true,
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
fun S08_CloudAccountSetupCompact() {
    ScreenshotTheme {
        CloudAccountSetupContent(
            useCompactLayout = true,
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
@ScreenshotPreviewMatrix
@Composable
fun S09_OnboardingBirthday() {
    ScreenshotTheme {
        OnboardingBirthdayContent(
            onBack = {},
            onBirthdaySelected = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S10_OnboardingRecommendations() {
    ScreenshotTheme {
        OnboardingRecommendationsContent(
            onBack = {},
            onKeepOn = {},
            onTurnOff = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S11_OnboardingDayBoundariesConnected() {
    ScreenshotTheme {
        OnboardingDayBoundariesContent(
            healthConnectStatus = HealthConnectStatus.CONNECTED,
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S12_OnboardingLocation() {
    ScreenshotTheme {
        OnboardingLocationContent(
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S13_OnboardingNotifications() {
    ScreenshotTheme {
        OnboardingNotificationsContent(
            onBack = {},
            onPrimaryAction = {},
            onSkip = {},
            recommendationsEnabled = true,
            hasDecision = false,
            hasPermission = false,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S14_OnboardingCompletionStreak() {
    ScreenshotTheme {
        OnboardingCompletionContent(
            shouldShowFinish = false,
            onContinue = {},
            onFinish = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S15_OnboardingCompletionFinal() {
    ScreenshotTheme {
        OnboardingCompletionContent(
            shouldShowFinish = true,
            onContinue = {},
            onFinish = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S16_OnboardingWelcomeBack() {
    ScreenshotTheme {
        WelcomeBackScreenContent(name = "Alex")
    }
}

private fun previewMemories(): List<MediaObject> =
    (1..12).map { index ->
        if (index % 3 == 0) {
            MediaObject.Video(
                uri = "sample$index",
                size = 2048,
                name = "VID_$index.mp4",
                timestamp = Clock.System.now(),
                duration = Duration.parse("30s"),
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
