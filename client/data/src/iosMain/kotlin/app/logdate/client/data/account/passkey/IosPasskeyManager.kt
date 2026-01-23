package app.logdate.client.data.account.passkey

import app.logdate.client.domain.account.passkey.AuthenticationOptions
import app.logdate.client.domain.account.passkey.PasskeyAuthenticationResult
import app.logdate.client.domain.account.passkey.PasskeyErrorCode
import app.logdate.client.domain.account.passkey.PasskeyException
import app.logdate.client.domain.account.passkey.PasskeyManager
import app.logdate.client.domain.account.passkey.PasskeyRegistrationResult
import app.logdate.client.domain.account.passkey.RegistrationOptions

/**
 * iOS stub implementation of [PasskeyManager].
 *
 * The AuthenticationServices-based passkey flow is not wired for iOS yet, so the
 * operations return a not-supported error until the native implementation lands.
 */
class IosPasskeyManager : PasskeyManager {
    override suspend fun createPasskey(
        options: RegistrationOptions
    ): Result<PasskeyRegistrationResult> {
        return Result.failure(
            PasskeyException(
                PasskeyErrorCode.NOT_SUPPORTED,
                "Passkey registration is not supported on iOS yet."
            )
        )
    }

    override suspend fun getPasskey(
        options: AuthenticationOptions
    ): Result<PasskeyAuthenticationResult> {
        return Result.failure(
            PasskeyException(
                PasskeyErrorCode.NOT_SUPPORTED,
                "Passkey authentication is not supported on iOS yet."
            )
        )
    }

    override fun isPasskeySupported(): Boolean = false
}
