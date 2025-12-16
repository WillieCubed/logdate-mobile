package app.logdate.client.domain.user

import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.repository.account.PasskeyAccountRepository
import kotlinx.coroutines.flow.first
import kotlin.uuid.ExperimentalUuidApi

/**
 * Use case for retrieving the current user ID.
 * 
 * According to the User Identity System design, a user ID comes from:
 * 1. The cloud account's primary ID (for cloud users)
 * 2. The device ID (for local-only users)
 * 
 * Device ID is ALWAYS available and is the fallback when no cloud account exists.
 */
@OptIn(ExperimentalUuidApi::class)
class GetUserIdUseCase(
    private val passkeyAccountRepository: PasskeyAccountRepository,
    private val deviceIdProvider: DeviceIdProvider
) {
    /**
     * Gets the current user ID.
     * @return Success with user ID if available, Error otherwise
     */
    suspend operator fun invoke(): UserIdResult {
        return try {
            // First try to get a cloud account ID (primary ID if user has cloud account)
            val account = passkeyAccountRepository.currentAccount.first()
            
            if (account != null) {
                // For cloud users, use the primary cloud account ID
                UserIdResult.Success(account.id.toString())
            } else {
                // For local-only users, the device ID serves as their user ID
                // Device ID is ALWAYS available according to the device identification system
                val deviceId = deviceIdProvider.getDeviceId().value.toString()
                UserIdResult.Success(deviceId)
            }
        } catch (e: Exception) {
            UserIdResult.Error(e.message ?: "Failed to retrieve user ID")
        }
    }
    
    /**
     * Result types for user ID requests.
     */
    sealed class UserIdResult {
        /** Successful retrieval of user ID. */
        data class Success(val userId: String) : UserIdResult()
        
        /** Error occurred retrieving user ID. */
        data class Error(val message: String) : UserIdResult()
    }
}