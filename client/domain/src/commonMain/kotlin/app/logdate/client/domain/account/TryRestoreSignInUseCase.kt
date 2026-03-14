package app.logdate.client.domain.account

import app.logdate.client.permissions.RestoreCredentialError
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier

/**
 * Attempts to silently sign in using a restore credential from the device's cloud backup.
 *
 * This should be invoked on app startup when the user is not already authenticated.
 * If no restore credential exists on the device (fresh install, no backup), the use
 * case returns [Result.NoCredential] so the caller can proceed with the normal
 * onboarding or sign-in flow.
 */
class TryRestoreSignInUseCase(
    private val passkeyAccountRepository: PasskeyAccountRepository,
) {
    sealed class Result {
        data class Success(
            val account: LogDateAccount,
        ) : Result()

        /** No restore credential available — proceed with normal sign-in flow. */
        data object NoCredential : Result()

        /** Restore sign-in failed for a recoverable reason (network error, server error). */
        data class Error(
            val message: String,
        ) : Result()
    }

    suspend operator fun invoke(): Result =
        try {
            val result = passkeyAccountRepository.signInWithRestoreKey()
            if (result.isSuccess) {
                Napier.i("Restore sign-in succeeded")
                Result.Success(result.getOrThrow())
            } else {
                val cause = result.exceptionOrNull()
                if (cause is RestoreCredentialError.NoCredential) {
                    Napier.d("No restore credential on device")
                    Result.NoCredential
                } else {
                    Napier.w("Restore sign-in returned an error", cause)
                    Result.Error(cause?.message ?: "Restore sign-in failed")
                }
            }
        } catch (e: Exception) {
            Napier.e("Unexpected error during restore sign-in", e)
            Result.Error(e.message ?: "Unexpected error")
        }
}
