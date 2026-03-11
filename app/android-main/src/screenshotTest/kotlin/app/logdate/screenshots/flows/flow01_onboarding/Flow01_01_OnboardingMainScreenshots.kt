package app.logdate.screenshots.flows.flow01_onboarding

import androidx.compose.runtime.Composable
import app.logdate.feature.onboarding.ui.CloudAccountSetupContent
import app.logdate.feature.onboarding.ui.MemoriesImportInfoScreen
import app.logdate.feature.onboarding.ui.OnboardingCompletionContent
import app.logdate.feature.onboarding.ui.OnboardingStartScreenContent
import app.logdate.feature.onboarding.ui.PersonalIntroContent
import app.logdate.feature.onboarding.ui.PersonalIntroStep
import app.logdate.feature.onboarding.ui.PersonalIntroUiState
import app.logdate.feature.onboarding.ui.WelcomeBackScreenContent
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

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
fun S03_OnboardingSignInSetupSync() {
    ScreenshotTheme {
        CloudAccountSetupContent(
            useCompactLayout = true,
            selectedOption = null,
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
fun S04_OnboardingImportInfo() {
    ScreenshotTheme {
        MemoriesImportInfoScreen(onBack = {}, onContinue = {})
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_PersonalIntroNameStep() {
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
fun S06_PersonalIntroBioStep() {
    ScreenshotTheme {
        PersonalIntroContent(
            uiState =
                PersonalIntroUiState(
                    name = "Alex",
                    bio = "Photographer, runner, and chronic over-writer of notes.",
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
fun S07_OnboardingCompleteStreak() {
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
fun S08_OnboardingCompleteFinal() {
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
fun S09_OnboardingWelcomeBack() {
    ScreenshotTheme {
        WelcomeBackScreenContent(name = "Alex")
    }
}
