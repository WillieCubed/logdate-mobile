package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier

/**
 * Use case for creating a new passkey for an existing account.
 * 
 * This operation requires the user to be authenticated with an existing
 * LogDate Cloud account and will add a new passkey to their account.
 */
class CreatePasskeyUseCase(
    private val passkeyAccountRepository: PasskeyAccountRepository,
) {
    
    /**
     * Request to create a new passkey.
     */
    data class CreatePasskeyRequest(
        val name: String? = null
    )
    
    /**
     * Result of a passkey creation operation.
     */
    sealed class CreatePasskeyResult {
        /**
         * Passkey was successfully created.
         */
        data class Success(val account: LogDateAccount) : CreatePasskeyResult()
        
        /**
         * Passkey creation failed.
         */
        data class Error(val message: String, val cause: Throwable? = null) : CreatePasskeyResult()
    }
    
    /**
     * Create a new passkey for the current user's account.
     * 
     * @param request The creation request
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(request: CreatePasskeyRequest): CreatePasskeyResult {
        // TODO: Implement actual passkey creation
        Napier.d("Stub implementation of CreatePasskeyUseCase")
        
        return try {
            // Fetch updated account info after creating passkey
            val result = passkeyAccountRepository.getAccountInfo()
            
            if (result.isSuccess) {
                CreatePasskeyResult.Success(result.getOrThrow())
            } else {
                val exception = result.exceptionOrNull()
                CreatePasskeyResult.Error(
                    message = exception?.message ?: "Failed to create passkey",
                    cause = exception
                )
            }
        } catch (e: Exception) {
            Napier.e("Failed to create passkey", e)
            CreatePasskeyResult.Error(
                message = "Failed to create passkey: ${e.message}",
                cause = e
            )
        }
    }
}