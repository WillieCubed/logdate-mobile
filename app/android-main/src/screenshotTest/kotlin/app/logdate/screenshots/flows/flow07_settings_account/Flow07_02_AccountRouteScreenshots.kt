package app.logdate.screenshots.flows.flow07_settings_account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.logdate.feature.core.account.ui.AccountCreationCompletionScreen
import app.logdate.feature.core.account.ui.AccountOnboardingUiState
import app.logdate.feature.core.account.ui.CloudAccountIntroContent
import app.logdate.feature.core.account.ui.DisplayNameSelectionContent
import app.logdate.feature.core.account.ui.PasskeyCreationContent
import app.logdate.feature.core.account.ui.PasskeyCreationUiState
import app.logdate.feature.core.account.ui.UsernameAvailability
import app.logdate.feature.core.account.ui.UsernameSelectionContent
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_CloudAccountIntroFromSettings() {
    ScreenshotTheme {
        CloudAccountIntroContent(
            isFromOnboarding = false,
            onContinue = {},
            onSkip = {},
            onBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_CloudAccountIntroFromOnboarding() {
    ScreenshotTheme {
        CloudAccountIntroContent(
            isFromOnboarding = true,
            onContinue = {},
            onSkip = {},
            onBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_UsernameSelectionEmpty() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        UsernameSelectionContent(
            uiState = AccountOnboardingUiState(),
            onUsernameChange = {},
            onCheckAvailability = {},
            onContinue = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_UsernameSelectionInvalid() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        UsernameSelectionContent(
            uiState =
                AccountOnboardingUiState(
                    username = "alex!",
                    usernameError = "Username can only contain letters, numbers, and underscores",
                ),
            onUsernameChange = {},
            onCheckAvailability = {},
            onContinue = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_UsernameSelectionChecking() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        UsernameSelectionContent(
            uiState =
                AccountOnboardingUiState(
                    username = "alex_j",
                    usernameAvailability = UsernameAvailability.CHECKING,
                ),
            onUsernameChange = {},
            onCheckAvailability = {},
            onContinue = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_UsernameSelectionAvailable() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        UsernameSelectionContent(
            uiState =
                AccountOnboardingUiState(
                    username = "alex_j",
                    usernameAvailability = UsernameAvailability.AVAILABLE,
                ),
            onUsernameChange = {},
            onCheckAvailability = {},
            onContinue = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_UsernameSelectionTaken() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        UsernameSelectionContent(
            uiState =
                AccountOnboardingUiState(
                    username = "alex_j",
                    usernameAvailability = UsernameAvailability.TAKEN,
                ),
            onUsernameChange = {},
            onCheckAvailability = {},
            onContinue = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_UsernameSelectionError() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        UsernameSelectionContent(
            uiState =
                AccountOnboardingUiState(
                    username = "alex_j",
                    usernameAvailability = UsernameAvailability.ERROR,
                    usernameError = "Unable to check availability right now",
                ),
            onUsernameChange = {},
            onCheckAvailability = {},
            onContinue = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S09_DisplayNameSelectionEmpty() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        DisplayNameSelectionContent(
            uiState = AccountOnboardingUiState(),
            onDisplayNameChange = {},
            onContinue = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S10_DisplayNameSelectionInvalid() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        DisplayNameSelectionContent(
            uiState =
                AccountOnboardingUiState(
                    displayName = "",
                    displayNameError = "Display name cannot be empty",
                ),
            onDisplayNameChange = {},
            onContinue = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S11_DisplayNameSelectionValid() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        DisplayNameSelectionContent(
            uiState =
                AccountOnboardingUiState(
                    displayName = "Alex Johnson",
                ),
            onDisplayNameChange = {},
            onContinue = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S12_PasskeyCreationIdle() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        PasskeyCreationContent(
            uiState = PasskeyCreationUiState(),
            onCreatePasskey = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S13_PasskeyCreationCreatingPasskey() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        PasskeyCreationContent(
            uiState = PasskeyCreationUiState(isCreatingPasskey = true),
            onCreatePasskey = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S14_PasskeyCreationCreatingAccount() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        PasskeyCreationContent(
            uiState = PasskeyCreationUiState(isCreatingAccount = true),
            onCreatePasskey = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S15_PasskeyCreationError() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        PasskeyCreationContent(
            uiState = PasskeyCreationUiState(errorMessage = "Failed to create passkey. Please try again."),
            onCreatePasskey = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S16_AccountCreationCompletion() {
    ScreenshotTheme {
        AccountCreationCompletionScreen(onFinish = {})
    }
}
