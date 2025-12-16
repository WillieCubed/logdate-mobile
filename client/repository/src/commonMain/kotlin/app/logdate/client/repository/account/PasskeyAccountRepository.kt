package app.logdate.client.repository.account

import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for passkey-based account management operations.
 * 
 * This repository handles account creation, authentication, and management
 * using WebAuthn passkeys for LogDate Cloud.
 */
interface PasskeyAccountRepository {
    
    /**
     * Current authenticated user account information.
     */
    val currentAccount: StateFlow<LogDateAccount?>
    
    /**
     * Current authentication status.
     */
    val isAuthenticated: StateFlow<Boolean>
    
    /**
     * Create a new LogDate Cloud account using passkey authentication.
     * 
     * @param request The account creation request containing user details
     * @return Result containing the created account or error
     */
    suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount>
    
    /**
     * Authenticate with an existing LogDate Cloud account using passkeys.
     * 
     * @param username Optional username hint for authentication
     * @return Result containing the authenticated account or error
     */
    suspend fun authenticateWithPasskey(username: String? = null): Result<LogDateAccount>
    
    /**
     * Check if a username is available for new account registration.
     * 
     * @param username The username to check
     * @return Result containing true if available, false if taken
     */
    suspend fun checkUsernameAvailability(username: String): Result<Boolean>
    
    /**
     * Sign out the current user and clear stored authentication data.
     */
    suspend fun signOut(): Result<Unit>
    
    /**
     * Get the currently authenticated account information.
     * 
     * @return The current account or null if not authenticated
     */
    suspend fun getCurrentAccount(): LogDateAccount?
    
    /**
     * Refresh account information from the server.
     * 
     * @return Result containing updated account information
     */
    suspend fun getAccountInfo(): Result<LogDateAccount>
    
    /**
     * Refresh the current authentication session using stored refresh token.
     * 
     * @return Result indicating success or failure of the refresh operation
     */
    suspend fun refreshAuthentication(): Result<Unit>
    
    /**
     * Delete a specific passkey credential from the user's account.
     * 
     * @param credentialId The ID of the passkey credential to delete
     * @return Result indicating success or failure of the deletion operation
     */
    suspend fun deletePasskey(credentialId: String): Result<Unit>
}

/**
 * Request data for creating a new LogDate Cloud account.
 */
data class AccountCreationRequest(
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val email: String? = null
)