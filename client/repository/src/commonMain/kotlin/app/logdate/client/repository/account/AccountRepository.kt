package app.logdate.client.repository.account

import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing user account profile information.
 */
interface AccountRepository {
    /**
     * Current authenticated user account information.
     */
    val currentAccount: Flow<LogDateAccount?>
    
    /**
     * Update the current user's profile information.
     * 
     * @param displayName The new display name (optional)
     * @param username The new username (optional)
     * @return Result containing the updated account information
     */
    suspend fun updateProfile(
        displayName: String? = null,
        username: String? = null
    ): Result<LogDateAccount>
    
    /**
     * Refresh account information from the server.
     */
    suspend fun refreshAccount(): Result<LogDateAccount>
    
    /**
     * Check if a username is available for registration.
     */
    suspend fun checkUsernameAvailability(username: String): Result<Boolean>
}