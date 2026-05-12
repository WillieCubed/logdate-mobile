package app.logdate.client.data.account.passkey

import app.logdate.client.domain.account.passkey.AuthenticationOptions
import app.logdate.client.domain.account.passkey.PasskeyAuthenticationResult
import app.logdate.client.domain.account.passkey.PasskeyErrorCode
import app.logdate.client.domain.account.passkey.PasskeyException
import app.logdate.client.domain.account.passkey.PasskeyManager
import app.logdate.client.domain.account.passkey.PasskeyRegistrationResult
import app.logdate.client.domain.account.passkey.RegistrationOptions

/**
 * Desktop implementation for the legacy domain passkey abstraction.
 *
 * This target has no native WebAuthn authenticator wired in-process, so it fails explicitly
 * instead of returning synthetic credentials that the server could never verify.
 */
class DesktopPasskeyManager : PasskeyManager {
    override suspend fun createPasskey(options: RegistrationOptions): Result<PasskeyRegistrationResult> =
        Result.failure(
            PasskeyException(
                errorCode = PasskeyErrorCode.NOT_SUPPORTED,
                message = UNAVAILABLE_MESSAGE,
            ),
        )

    override suspend fun getPasskey(options: AuthenticationOptions): Result<PasskeyAuthenticationResult> =
        Result.failure(
            PasskeyException(
                errorCode = PasskeyErrorCode.NOT_SUPPORTED,
                message = UNAVAILABLE_MESSAGE,
            ),
        )

    override fun isPasskeySupported(): Boolean = false

    private companion object {
        const val UNAVAILABLE_MESSAGE = "Passkeys are unavailable on this desktop build."
    }
}
