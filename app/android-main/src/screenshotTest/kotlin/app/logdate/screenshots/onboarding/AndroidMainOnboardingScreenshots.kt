package app.logdate.screenshots.onboarding

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
fun OnboardingStartRoute_Splash() {
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
fun OnboardingStartRoute_Landing() {
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
fun OnboardingSignInRoute_SetupSync() {
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
fun OnboardingSignInRoute_CreateAccountPlaceholder() {
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
fun OnboardingSignInRoute_SignInPlaceholder() {
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
fun OnboardingEntryRoute_Blank() {
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
fun OnboardingImportRoute_Info() {
    ScreenshotTheme {
        MemoriesImportInfoScreen(onBack = {}, onContinue = {})
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun PersonalIntroRoute_NameStep() {
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
fun PersonalIntroRoute_BioStep() {
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
fun OnboardingCompleteRoute_Streak() {
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
fun OnboardingCompleteRoute_Final() {
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
fun OnboardingWelcomeBackRoute() {
    ScreenshotTheme {
        WelcomeBackScreenContent(name = "Alex")
    }
}
