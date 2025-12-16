package app.logdate.shared.model

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Repository interface for managing cloud account operations.
 *
 * This repository handles all account-related functionality including
 * checking username availability, account creation, authentication,
 * and managing account credentials.
 */
interface CloudAccountRepository {
    /**
     * Checks if a username is available for registration.
     *
     * @param username The username to check.
     * @return True if the username is available, false otherwise.
     */
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
    
    /**
     * Begins the account creation process.
     *
     * @param username The desired username.
     * @param displayName The user's display name.
     * @param deviceInfo Optional device information.
     * @return A session token and challenge for passkey creation.
     */
    suspend fun beginAccountCreation(
        username: String,
        displayName: String,
        deviceInfo: DeviceInfo?
    ): Result<BeginAccountCreationResult>
    
    /**
     * Completes the account creation process with a passkey.
     *
     * @param sessionToken The session token from beginAccountCreation.
     * @param credentialId The passkey credential ID.
     * @param clientDataJSON The client data JSON from the passkey creation.
     * @param attestationObject The attestation object from the passkey creation.
     * @return The result of the account creation attempt.
     */
    suspend fun completeAccountCreation(
        sessionToken: String,
        credentialId: String,
        clientDataJSON: String,
        attestationObject: String
    ): Result<AuthenticationResult>
    
    /**
     * Gets the currently authenticated account.
     *
     * @return The current account, or null if not authenticated.
     */
    suspend fun getCurrentAccount(): CloudAccount?
    
    /**
     * Observes the current authentication state.
     *
     * @return A flow emitting the current account whenever it changes.
     */
    fun observeCurrentAccount(): Flow<CloudAccount?>
    
    /**
     * Refreshes the access token using a refresh token.
     *
     * @param refreshToken The refresh token.
     * @return The new access token if successful.
     */
    suspend fun refreshAccessToken(refreshToken: String): Result<String>
    
    /**
     * Signs out the current user.
     *
     * @return True if the sign-out was successful.
     */
    suspend fun signOut(): Result<Boolean>
    
    /**
     * Gets all passkeys associated with the current account.
     *
     * @return List of passkey credentials.
     */
    suspend fun getPasskeyCredentials(): Result<List<PasskeyCredential>>
    
    /**
     * Associates the user's local identity with their cloud account.
     *
     * @param userId The local user ID.
     * @param accountId The cloud account ID.
     * @return True if the association was successful.
     */
    suspend fun associateUserIdentity(userId: Uuid, accountId: String): Result<Boolean>
}