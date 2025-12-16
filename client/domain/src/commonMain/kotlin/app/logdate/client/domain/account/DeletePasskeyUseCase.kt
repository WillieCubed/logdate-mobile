package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository

/**
 * Use case for deleting a specific passkey credential from the user's account.
 * 
 * This operation requires the user to be authenticated and will remove the specified
 * passkey from their LogDate Cloud account.
 */
class DeletePasskeyUseCase(
    private val passkeyAccountRepository: PasskeyAccountRepository
) {
    
    /**
     * Request to delete a passkey credential.
     */
    data class DeletePasskeyRequest(
        val credentialId: String
    )
    
    /**
     * Result of a passkey deletion operation.
     */
    sealed class DeletePasskeyResult {
        /**
         * Passkey was successfully deleted.
         */
        object Success : DeletePasskeyResult()
        
        /**
         * Passkey deletion failed.
         */
        data class Error(val message: String, val cause: Throwable? = null) : DeletePasskeyResult()
    }
    
    /**
     * Delete the specified passkey credential.
     * 
     * @param request The deletion request containing the credential ID
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(request: DeletePasskeyRequest): DeletePasskeyResult {
        return try {
            val result = passkeyAccountRepository.deletePasskey(request.credentialId)
            
            if (result.isSuccess) {
                DeletePasskeyResult.Success
            } else {
                val exception = result.exceptionOrNull()
                DeletePasskeyResult.Error(
                    message = exception?.message ?: "Failed to delete passkey",
                    cause = exception
                )
            }
        } catch (e: Exception) {
            DeletePasskeyResult.Error(
                message = "Failed to delete passkey: ${e.message}",
                cause = e
            )
        }
    }
}