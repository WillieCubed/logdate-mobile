package app.logdate.screenshots.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.core.account.CloudAccountSignInContent
import app.logdate.feature.core.account.CloudAccountWelcomeContent
import app.logdate.feature.core.account.CloudAccountWelcomeScreen
import app.logdate.feature.core.account.PasskeyAccountCreationFinalContent
import app.logdate.feature.core.account.ui.AccountCreationCompletionScreen
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

// ─── Welcome ────────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun CloudAccount_Welcome() {
    ScreenshotTheme {
        CloudAccountWelcomeContent(
            onContinue = {},
            onSignIn = {},
            onSkip = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CloudAccount_Welcome_Dark() {
    ScreenshotTheme(darkTheme = true) {
        CloudAccountWelcomeContent(
            onContinue = {},
            onSignIn = {},
            onSkip = {},
        )
    }
}

// ─── Sign In ────────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun CloudAccount_SignIn() {
    ScreenshotTheme {
        CloudAccountSignInContent(
            username = "",
            onUsernameChange = {},
            serverDomain = "logdate.app",
            onServerDomainChange = {},
            isServerEditable = false,
            onServerDomainDoubleClick = {},
            onServerFocusLost = {},
            serverFocusRequester = FocusRequester(),
            onSignIn = {},
            onAccountRecovery = {},
            onPrivacyPolicy = {},
            onTermsOfService = {},
            showRecoveryPopup = false,
            onDismissRecoveryPopup = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun CloudAccount_SignIn_Filled() {
    ScreenshotTheme {
        CloudAccountSignInContent(
            username = "alex_j",
            onUsernameChange = {},
            serverDomain = "logdate.app",
            onServerDomainChange = {},
            isServerEditable = false,
            onServerDomainDoubleClick = {},
            onServerFocusLost = {},
            serverFocusRequester = FocusRequester(),
            onSignIn = {},
            onAccountRecovery = {},
            onPrivacyPolicy = {},
            onTermsOfService = {},
            showRecoveryPopup = false,
            onDismissRecoveryPopup = {},
            isSigningIn = false,
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun CloudAccount_SignIn_Loading() {
    ScreenshotTheme {
        CloudAccountSignInContent(
            username = "alex_j",
            onUsernameChange = {},
            serverDomain = "logdate.app",
            onServerDomainChange = {},
            isServerEditable = false,
            onServerDomainDoubleClick = {},
            onServerFocusLost = {},
            serverFocusRequester = FocusRequester(),
            onSignIn = {},
            onAccountRecovery = {},
            onPrivacyPolicy = {},
            onTermsOfService = {},
            showRecoveryPopup = false,
            onDismissRecoveryPopup = {},
            isSigningIn = true,
        )
    }
}

// ─── Account Creation Final ─────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun CloudAccount_CreationFinal() {
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
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun CloudAccount_CreationFinal_Creating() {
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
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun CloudAccount_CreationFinal_Error() {
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
        )
    }
}

// ─── Completion ─────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun CloudAccount_Completion() {
    ScreenshotTheme {
        AccountCreationCompletionScreen(onFinish = {})
    }
}
