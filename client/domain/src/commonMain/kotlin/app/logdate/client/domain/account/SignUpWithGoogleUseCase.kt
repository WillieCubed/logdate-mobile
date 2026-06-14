package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier

/**
 * Creates a new LogDate Cloud account with Google. [username] and [displayName] are optional hints
 * for the new account; the server may derive them from the Google profile when omitted.
 */
class SignUpWithGoogleUseCase(
    private val repository: PasskeyAccountRepository,
) {
    sealed interface Result {
        data class Success(
            val account: LogDateAccount,
        ) : Result

        data class Error(
            val error: GoogleAuthError,
        ) : Result
    }

    suspend operator fun invoke(
        username: String? = null,
        displayName: String? = null,
    ): Result =
        repository.signUpWithGoogle(username = username, displayName = displayName).fold(
            onSuccess = { Result.Success(it) },
            onFailure = { error ->
                Napier.w("Google sign-up failed", error)
                Result.Error(mapToGoogleAuthError(error))
            },
        )
}
