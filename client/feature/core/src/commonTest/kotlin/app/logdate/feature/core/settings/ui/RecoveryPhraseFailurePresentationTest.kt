package app.logdate.feature.core.settings.ui

import app.logdate.client.sync.RecoveryPhraseVerificationException
import app.logdate.client.sync.RecoveryPhraseVerificationReason
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_error
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_mismatch_error
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_no_proof_error
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_unavailable_error
import kotlin.test.Test
import kotlin.test.assertEquals

class RecoveryPhraseFailurePresentationTest {
    @Test
    fun invalidWordsHaveAnActionableExplanationWithoutTheExceptionText() {
        val failure = IllegalArgumentException("Invalid recovery phrase")

        assertEquals(RecoveryPhraseFailure.INVALID_WORDS, classifyRecoveryPhraseFailure(failure))
        assertEquals(RecoveryPhraseFailure.RETRYABLE, classifyRecoveryPhraseFailure(IllegalArgumentException("Storage rejected key")))
    }

    @Test
    fun storageFailuresDoNotPretendTheWordsWereWrong() {
        val failure = IllegalStateException("secret database location")

        assertEquals(RecoveryPhraseFailure.RETRYABLE, classifyRecoveryPhraseFailure(failure))
    }

    @Test
    fun accountProofFailuresRemainDistinctFromInvalidWords() {
        assertEquals(
            RecoveryPhraseFailure.MISMATCH,
            classifyRecoveryPhraseFailure(RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.MISMATCH)),
        )
        assertEquals(
            RecoveryPhraseFailure.UNAVAILABLE,
            classifyRecoveryPhraseFailure(RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)),
        )
        assertEquals(
            RecoveryPhraseFailure.NO_PROOF,
            classifyRecoveryPhraseFailure(RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.NO_PROOF)),
        )
        assertEquals(Res.string.recovery_phrase_entry_settings_error, RecoveryPhraseFailure.INVALID_WORDS.messageResource())
        assertEquals(Res.string.recovery_phrase_entry_settings_mismatch_error, RecoveryPhraseFailure.MISMATCH.messageResource())
        assertEquals(Res.string.recovery_phrase_entry_settings_unavailable_error, RecoveryPhraseFailure.UNAVAILABLE.messageResource())
        assertEquals(Res.string.recovery_phrase_entry_settings_no_proof_error, RecoveryPhraseFailure.NO_PROOF.messageResource())
    }
}
