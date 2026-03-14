package app.logdate.screenshots.flows.flow07_settings_account

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.core.account.CloudAccountSignInContent
import app.logdate.feature.core.account.CloudAccountWelcomeContent
import app.logdate.feature.core.account.PasskeyAccountCreationFinalContent
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

// ─── Welcome ────────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S01_CloudAccountWelcome() {
    ScreenshotTheme {
        CloudAccountWelcomeContent(
            onContinue = {},
            onSignIn = {},
            onSkip = {},
            serverSelectionState = ServerSelectionState(),
            onSelectServerPreset = {},
            onCustomServerUrlChange = {},
            onShowCustomServerInfo = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun S02_CloudAccountWelcomeDark() {
    ScreenshotTheme(darkTheme = true) {
        CloudAccountWelcomeContent(
            onContinue = {},
            onSignIn = {},
            onSkip = {},
            serverSelectionState = ServerSelectionState(),
            onSelectServerPreset = {},
            onCustomServerUrlChange = {},
            onShowCustomServerInfo = {},
        )
    }
}

// ─── Sign In ────────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S03_CloudAccountSignIn() {
    ScreenshotTheme {
        CloudAccountSignInContent(
            username = "",
            onUsernameChange = {},
            onSignIn = {},
            onAccountRecovery = {},
            onPrivacyPolicy = {},
            onTermsOfService = {},
            onBack = {},
            serverDisplayName = "LogDate Cloud",
            serverHandleDomain = "logdate.app",
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S04_CloudAccountSignInFilled() {
    ScreenshotTheme {
        CloudAccountSignInContent(
            username = "alex_j",
            onUsernameChange = {},
            onSignIn = {},
            onAccountRecovery = {},
            onPrivacyPolicy = {},
            onTermsOfService = {},
            onBack = {},
            serverDisplayName = "LogDate Cloud",
            serverHandleDomain = "logdate.app",
            isSigningIn = false,
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S05_CloudAccountSignInLoading() {
    ScreenshotTheme {
        CloudAccountSignInContent(
            username = "alex_j",
            onUsernameChange = {},
            onSignIn = {},
            onAccountRecovery = {},
            onPrivacyPolicy = {},
            onTermsOfService = {},
            onBack = {},
            serverDisplayName = "LogDate Cloud",
            serverHandleDomain = "logdate.app",
            isSigningIn = true,
        )
    }
}

// ─── Account Creation Final ─────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S06_CloudAccountCreationFinal() {
    ScreenshotTheme {
        PasskeyAccountCreationFinalContent(
            displayName = "Alex Johnson",
            username = "alex_j",
            bio = "",
            onBioChange = {},
            onCreateAccount = {},
            onBack = {},
            isCreatingAccount = false,
            errorMessage = null,
            onClearError = {},
            isPasskeySupported = true,
            handleDomain = "logdate.app",
            serverDisplayName = "LogDate Cloud",
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S07_CloudAccountCreationFinalCreating() {
    ScreenshotTheme {
        PasskeyAccountCreationFinalContent(
            displayName = "Alex Johnson",
            username = "alex_j",
            bio = "Explorer and photographer",
            onBioChange = {},
            onCreateAccount = {},
            onBack = {},
            isCreatingAccount = true,
            errorMessage = null,
            onClearError = {},
            isPasskeySupported = true,
            handleDomain = "logdate.app",
            serverDisplayName = "LogDate Cloud",
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S08_CloudAccountCreationFinalError() {
    ScreenshotTheme {
        PasskeyAccountCreationFinalContent(
            displayName = "Alex Johnson",
            username = "alex_j",
            bio = "Explorer and photographer",
            onBioChange = {},
            onCreateAccount = {},
            onBack = {},
            isCreatingAccount = false,
            errorMessage = "Failed to create passkey. Please try again.",
            onClearError = {},
            isPasskeySupported = true,
            handleDomain = "logdate.app",
            serverDisplayName = "LogDate Cloud",
        )
    }
}

