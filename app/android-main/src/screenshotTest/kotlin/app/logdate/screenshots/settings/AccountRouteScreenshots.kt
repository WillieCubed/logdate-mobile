package app.logdate.screenshots.settings

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
fun CloudAccountIntro_FromSettings() {
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
fun CloudAccountIntro_FromOnboarding() {
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
fun UsernameSelectionRoute_Empty() {
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
fun UsernameSelectionRoute_Invalid() {
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
fun UsernameSelectionRoute_Checking() {
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
fun UsernameSelectionRoute_Available() {
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
fun UsernameSelectionRoute_Taken() {
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
fun UsernameSelectionRoute_Error() {
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
fun DisplayNameSelectionRoute_Empty() {
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
fun DisplayNameSelectionRoute_Invalid() {
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
fun DisplayNameSelectionRoute_Valid() {
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
fun PasskeyCreationRoute_Idle() {
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
fun PasskeyCreationRoute_CreatingPasskey() {
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
fun PasskeyCreationRoute_CreatingAccount() {
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
fun PasskeyCreationRoute_Error() {
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
fun AccountCreationCompletionRoute() {
    ScreenshotTheme {
        AccountCreationCompletionScreen(onFinish = {})
    }
}
