package app.logdate.screenshots.components.settings_account

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.client.repository.account.LinkedSignInProvider
import app.logdate.feature.core.settings.account.AccountContent
import app.logdate.feature.core.settings.account.AccountDestinations
import app.logdate.feature.core.settings.account.AccountHeader
import app.logdate.feature.core.settings.account.AccountUiState
import app.logdate.feature.core.settings.account.EmailRow
import app.logdate.feature.core.settings.account.RecoveryKeyRow
import app.logdate.feature.core.settings.account.ServerRow
import app.logdate.feature.core.settings.account.SignInSummary
import app.logdate.feature.core.settings.account.recoverykey.KeyStatus
import app.logdate.feature.core.settings.account.recoverykey.PhraseStatus
import app.logdate.feature.core.settings.account.recoverykey.RecoveryKeyContent
import app.logdate.feature.core.settings.account.recoverykey.RecoveryKeyDestinations
import app.logdate.feature.core.settings.account.recoverykey.RecoveryKeyUiState
import app.logdate.feature.core.settings.account.recoverykey.ReplaceServerKeyDialog
import app.logdate.feature.core.settings.account.recoverykey.TurnOnRecoveryKeyDialog
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.shared.SharedScreenshotScene
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import com.android.tools.screenshot.PreviewTest

// ─── Shared scenes, also rendered by the desktop lane ───────────────────────────

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun RecoveryKey_Setup() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.RecoveryKeySetup) }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun RecoveryKey_On() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.RecoveryKeyOn) }
}

// ─── Other states ───────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryKey_PhraseNotConfirmed() {
    ScreenshotTheme { RecoveryKey(ready(phrase = PhraseStatus.NOT_CONFIRMED, key = KeyStatus.OFF)) }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryKey_FromOtherPhrase() {
    ScreenshotTheme { RecoveryKey(ready(key = KeyStatus.ON_FROM_OTHER_PHRASE)) }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryKey_WithoutPhrase() {
    ScreenshotTheme { RecoveryKey(ready(phrase = PhraseStatus.MISSING, key = KeyStatus.ON_WITHOUT_PHRASE)) }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryKey_Replaced() {
    ScreenshotTheme { RecoveryKey(ready(key = KeyStatus.ON).copy(replacedServerKey = true)) }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryKey_IdentityChanged() {
    ScreenshotTheme { RecoveryKey(ready(key = KeyStatus.ON).copy(error = IdentityRecoveryFailure.IDENTITY_CHANGED)) }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryKey_TurnOnDialog() {
    ScreenshotTheme { TurnOnRecoveryKeyDialog(state = ready(key = KeyStatus.OFF), onConfirm = {}, onDismiss = {}) }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryKey_ReplaceDialog() {
    ScreenshotTheme { ReplaceServerKeyDialog(state = ready(key = KeyStatus.ON), onConfirm = {}, onDismiss = {}) }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun AccountSettings_RecoveryKeyOffered() {
    ScreenshotTheme {
        AccountContent(
            state =
                AccountUiState.SignedIn(
                    header = AccountHeader(displayName = "Alex Rivera", username = "alex"),
                    signIn = SignInSummary.Known(passkeyCount = 2, linkedProviders = listOf(LinkedSignInProvider.Kind.GOOGLE)),
                    hasRecoveryPhrase = true,
                    email = EmailRow(address = "alex@example.com", isVerified = true, canVerify = false),
                    recoveryKey = RecoveryKeyRow.OFF,
                    server = ServerRow(name = "LogDate Cloud", host = "cloud.logdate.app", isLogDateCloud = true),
                    isSigningOut = false,
                ),
            destinations = AccountDestinations({}, {}, {}, {}, {}, {}, {}, {}),
            onOpenEmailVerification = {},
            onSignOut = {},
        )
    }
}

@Composable
private fun RecoveryKey(state: RecoveryKeyUiState) {
    RecoveryKeyContent(
        state = state,
        destinations = RecoveryKeyDestinations(onBack = {}, onRecoveryPhrase = {}, onEnterPhrase = {}),
        onTurnOn = {},
        onReplaceServerKey = {},
        onRetry = {},
        onDismissError = {},
    )
}

private fun ready(
    phrase: PhraseStatus = PhraseStatus.CONFIRMED,
    key: KeyStatus,
) = RecoveryKeyUiState.Ready(
    handle = "alex.logdate.app",
    serverName = "LogDate Cloud",
    phrase = phrase,
    key = key,
)
