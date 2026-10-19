package app.logdate.client.data.account

import app.logdate.client.networking.IdentityApiClientContract
import app.logdate.client.networking.PasskeyStepUpProofDto
import app.logdate.client.permissions.PasskeyErrorCodes
import app.logdate.client.permissions.PasskeyException
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.shared.model.PasskeyAuthenticationOptions
import kotlinx.coroutines.CancellationException

/**
 * Gets a fresh passkey confirmation for a recovery-key change. The server refuses those changes
 * without one, so a stolen session token alone cannot make them.
 */
fun interface IdentityStepUp {
    /**
     * Asks the person to confirm with one of their passkeys and returns the proof to send with the
     * change. Fails with [IdentityRecoveryException] when the prompt is closed or cannot be shown.
     */
    suspend fun confirm(accessToken: String): PasskeyStepUpProofDto
}

/**
 * [IdentityStepUp] with the platform's passkey prompt.
 *
 * @param beginStepUp asks the server for a step-up challenge, as [IdentityApiClientContract.beginRecoveryKeyStepUp] does
 */
internal class PasskeyIdentityStepUp(
    private val beginStepUp: suspend (accessToken: String) -> Result<PasskeyAuthenticationOptions>,
    private val passkeyManager: PasskeyManager,
    private val credentialCodec: PasskeyCredentialCodec,
) : IdentityStepUp {
    override suspend fun confirm(accessToken: String): PasskeyStepUpProofDto {
        val options = beginStepUp(accessToken).getOrThrow()
        val credentialJson =
            passkeyManager.authenticateWithPasskey(options).getOrElse { error ->
                if (error is CancellationException) throw error
                throw IdentityRecoveryException(stepUpFailureFor(error), "The passkey confirmation did not finish", error)
            }
        val credential =
            try {
                credentialCodec.parseAssertionResponse(credentialJson)
            } catch (error: IllegalArgumentException) {
                throw IdentityRecoveryException(
                    IdentityRecoveryFailure.STEP_UP_FAILED,
                    "The platform returned a passkey assertion that could not be read",
                    error,
                )
            }
        return PasskeyStepUpProofDto(challenge = options.challenge, credential = credential)
    }

    private fun stepUpFailureFor(error: Throwable): IdentityRecoveryFailure =
        when ((error as? PasskeyException)?.errorCode) {
            PasskeyErrorCodes.USER_CANCELLED -> IdentityRecoveryFailure.STEP_UP_CANCELLED
            PasskeyErrorCodes.NOT_SUPPORTED -> IdentityRecoveryFailure.STEP_UP_UNAVAILABLE
            else -> IdentityRecoveryFailure.STEP_UP_FAILED
        }
}
