package app.logdate.feature.core.settings.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.sync.RecoveryPhraseVerificationException
import app.logdate.client.sync.RecoveryPhraseVerificationReason
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class RecoveryPhraseFailureUiTest {
    @Test
    fun mismatchedPhraseStaysOnRecoveryWithAnotherMethod() =
        assertFailure(
            RecoveryPhraseVerificationReason.MISMATCH,
            "This phrase doesn’t unlock this account’s current journal. Check the words or use another signed-in device.",
        )

    @Test
    fun temporaryVerificationFailureOffersRetry() =
        assertFailure(
            RecoveryPhraseVerificationReason.UNAVAILABLE,
            "LogDate can’t verify the phrase now. Check your connection and that LogDate is up to date, then retry.",
        )

    @Test
    fun noAccountProofExplainsRecoverySources() =
        assertFailure(
            RecoveryPhraseVerificationReason.NO_PROOF,
            "LogDate can’t confirm this phrase for your account. Restore a retained backup or transfer from another signed-in device.",
        )

    @Test
    fun invalidMnemonicIsNotAnAccountMismatch() =
        assertFailure(
            IllegalArgumentException("Invalid recovery phrase"),
            "Those words aren’t a valid recovery phrase. Check their spelling and order.",
        )

    private fun assertFailure(
        reason: RecoveryPhraseVerificationReason,
        expectedCopy: String,
    ) = assertFailure(RecoveryPhraseVerificationException(reason), expectedCopy)

    private fun assertFailure(
        failure: Throwable,
        expectedCopy: String,
    ) = runDesktopComposeUiTest(width = 720, height = 1200) {
        var recovered = false
        setContent {
            LogDateTheme {
                RecoveryPhraseEntrySettingsContent(
                    onBack = {},
                    onRecovered = { recovered = true },
                    submitRecovery = { _, onResult -> onResult(Result.failure(failure)) },
                    initialWords = List(12) { "word" },
                )
            }
        }
        onNodeWithText("Continue").assertIsDisplayed().performClick()
        onNodeWithText(expectedCopy).assertIsDisplayed()
        assertFalse(recovered)
        onNodeWithText("Recovery phrase").assertIsDisplayed()
    }
}
