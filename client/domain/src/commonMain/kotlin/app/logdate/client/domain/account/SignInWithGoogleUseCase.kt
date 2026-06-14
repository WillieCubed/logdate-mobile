package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier

/**
 * Signs in to an existing LogDate Cloud account with Google. Obtaining the Google ID token and
 * exchanging it with the server happen in the repository; this use case maps the outcome to
 * semantic [GoogleAuthError] categories for the UI.
 */
class SignInWithGoogleUseCase(
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

    suspend operator fun invoke(): Result =
        repository.signInWithGoogle().fold(
            onSuccess = { Result.Success(it) },
            onFailure = { error ->
                Napier.w("Google sign-in failed", error)
                Result.Error(mapToGoogleAuthError(error))
            },
        )
}
