package app.logdate.client.device

import app.logdate.shared.model.LogDateAccount

/**
 * Cross-platform interface for managing LogDate Cloud accounts in the system account managers
 */
interface PlatformAccountManager {
    
    /**
     * Add a LogDate Cloud account to the system account manager
     */
    suspend fun addAccount(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String
    ): Result<Unit>
    
    /**
     * Update account information in the system account manager
     */
    suspend fun updateAccount(
        account: LogDateAccount,
        backendUrl: String
    ): Result<Unit>
    
    /**
     * Update stored tokens for an account
     */
    suspend fun updateTokens(
        username: String,
        accessToken: String,
        refreshToken: String
    ): Result<Unit>
    
    /**
     * Remove an account from the system account manager
     */
    suspend fun removeAccount(username: String): Result<Unit>
    
    /**
     * Get all stored LogDate Cloud accounts
     */
    suspend fun getStoredAccounts(): Result<List<PlatformAccountInfo>>
    
    /**
     * Get stored tokens for a specific account
     */
    suspend fun getTokens(username: String): Result<TokenPair?>
    
    /**
     * Clear all stored tokens (for security purposes)
     */
    suspend fun clearAllTokens(): Result<Unit>
}

/**
 * Information about a platform-stored account
 */
data class PlatformAccountInfo(
    val username: String,
    val displayName: String,
    val userId: String?,
    val backendUrl: String?
)

/**
 * Access and refresh token pair
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String
)

/**
 * Exception thrown when platform account manager operations fail
 */
class PlatformAccountException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)