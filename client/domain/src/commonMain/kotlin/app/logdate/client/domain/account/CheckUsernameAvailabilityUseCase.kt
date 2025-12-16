package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

/**
 * Use case for checking username availability with debouncing for real-time validation
 */
class CheckUsernameAvailabilityUseCase(
    private val passkeyAccountRepository: PasskeyAccountRepository
) {
    
    sealed class Result {
        data class Success(val isAvailable: Boolean) : Result()
        data class Error(val error: AvailabilityCheckError) : Result()
    }
    
    sealed class AvailabilityCheckError {
        object InvalidUsername : AvailabilityCheckError()
        object NetworkError : AvailabilityCheckError()
        data class Unknown(val message: String) : AvailabilityCheckError()
    }
    
    suspend operator fun invoke(
        username: String,
        debounceMs: Long = 300L
    ): Result {
        return try {
            // Validate username format before checking availability
            val validationError = validateUsername(username)
            if (validationError != null) {
                return Result.Error(validationError)
            }
            
            // Debounce to avoid excessive API calls during typing
            delay(debounceMs)
            
            val availabilityResult = passkeyAccountRepository.checkUsernameAvailability(username)
            
            if (availabilityResult.isSuccess) {
                val isAvailable = availabilityResult.getOrThrow()
                Napier.d("Username availability check: '$username' is ${if (isAvailable) "available" else "taken"}")
                Result.Success(isAvailable)
            } else {
                val exception = availabilityResult.exceptionOrNull()
                Napier.e("Failed to check username availability", exception)
                Result.Error(AvailabilityCheckError.NetworkError)
            }
            
        } catch (e: Exception) {
            Napier.e("Unexpected error during username availability check", e)
            Result.Error(AvailabilityCheckError.Unknown(e.message ?: "Unknown error"))
        }
    }
    
    private fun validateUsername(username: String): AvailabilityCheckError? {
        if (username.isBlank()) {
            return AvailabilityCheckError.InvalidUsername
        }
        
        if (username.length < 3 || username.length > 50) {
            return AvailabilityCheckError.InvalidUsername
        }
        
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return AvailabilityCheckError.InvalidUsername
        }
        
        return null
    }
}