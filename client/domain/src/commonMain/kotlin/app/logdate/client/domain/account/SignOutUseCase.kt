package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import io.github.aakira.napier.Napier

/**
 * Use case for signing out of LogDate Cloud account
 */
class SignOutUseCase(
    private val passkeyAccountRepository: PasskeyAccountRepository
) {
    
    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }
    
    suspend operator fun invoke(): Result {
        return try {
            val signOutResult = passkeyAccountRepository.signOut()
            
            if (signOutResult.isSuccess) {
                Napier.i("Successfully signed out")
                Result.Success
            } else {
                val exception = signOutResult.exceptionOrNull()
                Napier.e("Failed to sign out", exception)
                Result.Error(exception?.message ?: "Unknown error during sign out")
            }
            
        } catch (e: Exception) {
            Napier.e("Unexpected error during sign out", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }
}