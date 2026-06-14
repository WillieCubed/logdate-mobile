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

    /**
     * Create a restore key backed up to the device's encrypted cloud backup.
     * Should be called after successful account creation.
     * Non-fatal — returns success even if the device does not support E2EE backup.
     */
    suspend fun createRestoreKey(): Result<Unit>

    /**
     * Attempt to silently sign in using a restore credential from the device's cloud backup.
     * Returns failure if no restore credential is available (fresh install, no backup, or
     * the server does not support restore credentials).
     */
    suspend fun signInWithRestoreKey(): Result<LogDateAccount>

    /**
     * Delete the restore credential. Should be called on sign-out.
     * Non-fatal — failures are silently ignored.
     */
    suspend fun deleteRestoreKey(): Result<Unit>

    /**
     * Create a LogDate Cloud account from a Google account. Obtains a Google ID token through the
     * platform credential flow and exchanges it with the server. Defaults to failure so platforms
     * and test doubles without Google support don't need to implement it.
     */
    suspend fun signUpWithGoogle(
        username: String? = null,
        displayName: String? = null,
    ): Result<LogDateAccount> = Result.failure(UnsupportedOperationException("Google sign-up is not supported"))

    /**
     * Authenticate with an existing LogDate Cloud account using Google.
     */
    suspend fun signInWithGoogle(): Result<LogDateAccount> =
        Result.failure(UnsupportedOperationException("Google sign-in is not supported"))
}

/**
 * Request data for creating a new LogDate Cloud account.
 */
data class AccountCreationRequest(
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val email: String? = null,
)
