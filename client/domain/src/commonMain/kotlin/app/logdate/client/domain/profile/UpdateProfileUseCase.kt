package app.logdate.client.domain.profile

import app.logdate.client.repository.account.AccountRepository
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier

/**
 * Use case for updating user profile information.
 */
class UpdateProfileUseCase(
    private val accountRepository: AccountRepository
) {
    
    sealed class Result {
        data class Success(val account: LogDateAccount) : Result()
        data class Error(val error: ProfileUpdateError) : Result()
    }
    
    sealed class ProfileUpdateError {
        data object InvalidDisplayName : ProfileUpdateError()
        data object InvalidUsername : ProfileUpdateError()
        data object NetworkError : ProfileUpdateError()
        data class Unknown(val message: String) : ProfileUpdateError()
    }
    
    suspend operator fun invoke(
        displayName: String? = null,
        username: String? = null
    ): Result {
        return try {
            // Validate inputs
            displayName?.let { name ->
                val validationError = validateDisplayName(name)
                if (validationError != null) {
                    return Result.Error(validationError)
                }
            }
            
            username?.let { handle ->
                val validationError = validateUsername(handle)
                if (validationError != null) {
                    return Result.Error(validationError)
                }
            }
            
            // Attempt to update the profile
            val updateResult = accountRepository.updateProfile(
                displayName = displayName,
                username = username
            )
            
            if (updateResult.isSuccess) {
                val updatedAccount = updateResult.getOrThrow()
                Napier.d("Profile updated successfully for user: ${updatedAccount.username}")
                Result.Success(updatedAccount)
            } else {
                val exception = updateResult.exceptionOrNull()
                Napier.e("Failed to update profile", exception)
                Result.Error(ProfileUpdateError.NetworkError)
            }
            
        } catch (e: Exception) {
            Napier.e("Unexpected error during profile update", e)
            Result.Error(ProfileUpdateError.Unknown(e.message ?: "Unknown error"))
        }
    }
    
    private fun validateDisplayName(displayName: String): ProfileUpdateError? {
        return when {
            displayName.isBlank() -> ProfileUpdateError.InvalidDisplayName
            displayName.length > 100 -> ProfileUpdateError.InvalidDisplayName
            else -> null
        }
    }
    
    private fun validateUsername(username: String): ProfileUpdateError? {
        return when {
            username.isBlank() -> ProfileUpdateError.InvalidUsername
            username.length < 3 -> ProfileUpdateError.InvalidUsername
            username.length > 30 -> ProfileUpdateError.InvalidUsername
            !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> ProfileUpdateError.InvalidUsername
            username.startsWith("_") || username.endsWith("_") -> ProfileUpdateError.InvalidUsername
            else -> null
        }
    }
    
}