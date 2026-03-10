package app.logdate.screenshots.flows.flow01_onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.client.billing.model.LogDateBackupPlanOption
import app.logdate.feature.onboarding.ui.CloudAccountSetupContent
import app.logdate.feature.onboarding.ui.CloudSetupOption
import app.logdate.feature.onboarding.ui.MemoriesImportInfoScreen
import app.logdate.feature.onboarding.ui.OnboardingCompletionContent
import app.logdate.feature.onboarding.ui.OnboardingStartScreenContent
import app.logdate.feature.onboarding.ui.PersonalIntroContent
import app.logdate.feature.onboarding.ui.PersonalIntroStep
import app.logdate.feature.onboarding.ui.PersonalIntroUiState
import app.logdate.feature.onboarding.ui.WelcomeBackScreenContent
import app.logdate.screenshots.common.PlaceholderRouteFrame
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
fun S04_OnboardingSignInCreateAccountPlaceholder() {
    ScreenshotTheme {
        CloudAccountSetupContent(
            useCompactLayout = true,
            selectedOption = CloudSetupOption.CREATE_ACCOUNT,
            onBack = {},
            onOptionSelected = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = { _: LogDateBackupPlanOption -> },
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_OnboardingSignInSignInPlaceholder() {
    ScreenshotTheme {
        CloudAccountSetupContent(
            useCompactLayout = true,
            selectedOption = CloudSetupOption.SIGN_IN,
            onBack = {},
            onOptionSelected = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = { _: LogDateBackupPlanOption -> },
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_OnboardingEntryBlank() {
    ScreenshotTheme {
        PlaceholderRouteFrame {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Current route is intentionally blank.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_OnboardingImportInfo() {
    ScreenshotTheme {
        MemoriesImportInfoScreen(onBack = {}, onContinue = {})
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_PersonalIntroNameStep() {
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
fun S09_PersonalIntroBioStep() {
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
fun S10_OnboardingCompleteStreak() {
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
fun S11_OnboardingCompleteFinal() {
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
fun S12_OnboardingWelcomeBack() {
    ScreenshotTheme {
        WelcomeBackScreenContent(name = "Alex")
    }
}
