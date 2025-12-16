package app.logdate.client.sync.cloud.account

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.shared.model.AccountCredentials
import app.logdate.shared.model.AuthenticationResult
import app.logdate.shared.model.BeginAccountCreationResult
import app.logdate.shared.model.CloudAccount
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.DeviceInfo
import app.logdate.shared.model.DeviceType
import app.logdate.shared.model.PasskeyCredential
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Default implementation of [CloudAccountRepository].
 *
 * This implementation uses the [CloudApiClient] for network operations and
 * securely stores credentials using [KeyValueStorage].
 *
 * @param apiClient The client for LogDate Cloud API communication.
 * @param secureStorage The storage for secure data like tokens.
 * @param platformInfoProvider Provider for platform-specific device information.
 */
class DefaultCloudAccountRepository(
    private val apiClient: CloudApiClient,
    private val secureStorage: KeyValueStorage,
    private val platformInfoProvider: PlatformInfoProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CloudAccountRepository {

    private val accountFlow = MutableStateFlow<CloudAccount?>(null)
    
    /**
     * Key constants for secure storage.
     */
    private object StorageKeys {
        const val ACCESS_TOKEN = "cloud_access_token"
        const val REFRESH_TOKEN = "cloud_refresh_token"
        const val ACCOUNT_ID = "cloud_account_id"
        const val ACCOUNT_USERNAME = "cloud_username"
        const val ACCOUNT_DISPLAY_NAME = "cloud_display_name"
        const val USER_ID = "cloud_user_id"
        const val CREATED_AT = "cloud_created_at"
        const val UPDATED_AT = "cloud_updated_at"
        const val PASSKEY_IDS = "cloud_passkey_ids"
    }
    
    init {
        // Initialize with a coroutine to avoid blocking the main thread
        coroutineScope.launch {
            loadStoredAccount()
        }
    }
    
    /**
     * Loads account information from secure storage if available.
     */
    private suspend fun loadStoredAccount() {
        try {
            // Check if we have stored credentials
            val accessToken = secureStorage.getString(StorageKeys.ACCESS_TOKEN)
            val accountId = secureStorage.getString(StorageKeys.ACCOUNT_ID)
            val username = secureStorage.getString(StorageKeys.ACCOUNT_USERNAME)
            val displayName = secureStorage.getString(StorageKeys.ACCOUNT_DISPLAY_NAME)
            val userIdString = secureStorage.getString(StorageKeys.USER_ID)
            val createdAtString = secureStorage.getString(StorageKeys.CREATED_AT)
            val updatedAtString = secureStorage.getString(StorageKeys.UPDATED_AT)
            val passkeyIdsString = secureStorage.getString(StorageKeys.PASSKEY_IDS)
            
            if (accountId != null && username != null && displayName != null && 
                userIdString != null && createdAtString != null && updatedAtString != null) {
                val userId = Uuid.parse(userIdString)
                val createdAt = Instant.parse(createdAtString)
                val updatedAt = Instant.parse(updatedAtString)
                val passkeyIds = passkeyIdsString?.split(",") ?: emptyList()
                
                // Convert String IDs to UUIDs
                val account = CloudAccount(
                    id = Uuid.parse(accountId),
                    username = username,
                    displayName = displayName,
                    userId = userId,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    passkeyCredentialIds = passkeyIds.map { Uuid.parse(it) }
                )
                
                accountFlow.value = account
                Napier.d("Loaded stored account: $username")
            }
        } catch (e: Exception) {
            Napier.e("Failed to load stored account", e)
            // If loading fails, we just keep the account as null
        }
    }
    
    /**
     * Checks if a username is available for registration.
     *
     * @param username The username to check.
     * @return True if the username is available, false otherwise.
     */
    override suspend fun isUsernameAvailable(username: String): Result<Boolean> {
        return try {
            // Using the RESTful approach
            val result = apiClient.checkUsernameAvailability(username)
            
            result.map { response ->
                response.available
            }
        } catch (e: Exception) {
            Napier.e("Error checking username availability", e)
            Result.failure(e)
        }
    }
    
    /**
     * Begins the account creation process.
     *
     * @param username The desired username.
     * @param displayName The user's display name.
     * @param deviceInfo Optional device information.
     * @return A session token and challenge for passkey creation.
     */
    override suspend fun beginAccountCreation(
        username: String,
        displayName: String,
        deviceInfo: DeviceInfo?
    ): Result<BeginAccountCreationResult> {
        return try {
            val deviceInfoDto = deviceInfo?.let {
                app.logdate.shared.model.DeviceInfoDto(
                    platform = it.platform,
                    deviceName = it.deviceName,
                    deviceType = it.deviceType.name
                )
            } ?: createDefaultDeviceInfo()
            
            val request = app.logdate.shared.model.BeginAccountCreationRequest(
                username = username,
                displayName = displayName,
                bio = null // Optional bio not supported in domain model yet
            )
            
            val result = apiClient.beginAccountCreation(request)
            
            result.map { response ->
                BeginAccountCreationResult(
                    sessionToken = response.data.sessionToken,
                    challenge = response.data.registrationOptions.challenge,
                    rpId = response.data.registrationOptions.user.id,
                    rpName = response.data.registrationOptions.user.name,
                    userId = Uuid.parse(response.data.registrationOptions.user.id),
                    username = response.data.registrationOptions.user.name,
                    displayName = response.data.registrationOptions.user.displayName,
                    timeout = response.data.registrationOptions.timeout
                )
            }
        } catch (e: Exception) {
            Napier.e("Error beginning account creation", e)
            Result.failure(e)
        }
    }
    
    /**
     * Completes the account creation process with a passkey.
     *
     * @param sessionToken The session token from beginAccountCreation.
     * @param credentialId The passkey credential ID.
     * @param clientDataJSON The client data JSON from the passkey creation.
     * @param attestationObject The attestation object from the passkey creation.
     * @return The result of the account creation attempt.
     */
    override suspend fun completeAccountCreation(
        sessionToken: String,
        credentialId: String,
        clientDataJSON: String,
        attestationObject: String
    ): Result<AuthenticationResult> {
        return try {
            val request = app.logdate.shared.model.CompleteAccountCreationRequest(
                sessionToken = sessionToken,
                credential = app.logdate.shared.model.PasskeyCredentialResponse(
                    id = credentialId,
                    rawId = credentialId, // In a real implementation, these would be different
                    response = app.logdate.shared.model.PasskeyAuthenticatorResponse(
                        clientDataJSON = clientDataJSON,
                        attestationObject = attestationObject
                    ),
                    type = "public-key"
                )
            )
            
            val result = apiClient.completeAccountCreation(request)
            
            result.map { response ->
                if (response.success) {
                    val accountDto = response.data.account
                    val tokensDto = response.data.tokens
                    
                    // Create the account and credentials
                    val account = CloudAccount(
                        id = Uuid.parse(accountDto.username), // Generate a UUID from the username
                        username = accountDto.username,
                        displayName = accountDto.displayName,
                        userId = Uuid.parse(accountDto.username), // Using username as user ID for simplicity
                        createdAt = accountDto.createdAt,
                        updatedAt = accountDto.updatedAt,
                        passkeyCredentialIds = accountDto.passkeyCredentialIds.map { Uuid.parse(it) }
                    )
                    
                    val credentials = AccountCredentials(
                        accessToken = tokensDto.accessToken,
                        refreshToken = tokensDto.refreshToken,
                        expiresIn = 3600 // Default to 1 hour if not specified
                    )
                    
                    // Store the account and credentials
                    storeAccountAndCredentials(account, credentials)
                    
                    // Update the current account flow
                    accountFlow.value = account
                    
                    AuthenticationResult.Success(account, credentials)
                } else {
                    AuthenticationResult.Error(
                        errorCode = "ACCOUNT_CREATION_FAILED",
                        message = "Failed to create account"
                    )
                }
            }
        } catch (e: Exception) {
            Napier.e("Error completing account creation", e)
            Result.failure(e)
        }
    }
    
    /**
     * Gets the currently authenticated account.
     *
     * @return The current account, or null if not authenticated.
     */
    override suspend fun getCurrentAccount(): CloudAccount? {
        return accountFlow.value
    }
    
    /**
     * Observes the current authentication state.
     *
     * @return A flow emitting the current account whenever it changes.
     */
    override fun observeCurrentAccount(): Flow<CloudAccount?> {
        return accountFlow.asStateFlow()
    }
    
    /**
     * Refreshes the access token using a refresh token.
     *
     * @param refreshToken The refresh token.
     * @return The new access token if successful.
     */
    override suspend fun refreshAccessToken(refreshToken: String): Result<String> {
        return try {
            val result = apiClient.refreshAccessToken(refreshToken)
            
            result.onSuccess { newToken ->
                // Update the stored token
                secureStorage.putString(StorageKeys.ACCESS_TOKEN, newToken)
            }
            
            result
        } catch (e: Exception) {
            Napier.e("Error refreshing access token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Signs out the current user.
     *
     * @return True if the sign-out was successful.
     */
    override suspend fun signOut(): Result<Boolean> {
        return try {
            // Clear all stored account information
            secureStorage.remove(StorageKeys.ACCESS_TOKEN)
            secureStorage.remove(StorageKeys.REFRESH_TOKEN)
            secureStorage.remove(StorageKeys.ACCOUNT_ID)
            secureStorage.remove(StorageKeys.ACCOUNT_USERNAME)
            secureStorage.remove(StorageKeys.ACCOUNT_DISPLAY_NAME)
            secureStorage.remove(StorageKeys.USER_ID)
            secureStorage.remove(StorageKeys.CREATED_AT)
            secureStorage.remove(StorageKeys.UPDATED_AT)
            secureStorage.remove(StorageKeys.PASSKEY_IDS)
            
            // Clear the current account
            accountFlow.value = null
            
            Result.success(true)
        } catch (e: Exception) {
            Napier.e("Error signing out", e)
            Result.failure(e)
        }
    }
    
    /**
     * Gets all passkeys associated with the current account.
     *
     * @return List of passkey credentials.
     */
    override suspend fun getPasskeyCredentials(): Result<List<PasskeyCredential>> {
        // This would typically call an API endpoint to get the list of passkeys
        // For now, we'll just return the stored credential IDs with minimal info
        return try {
            val account = getCurrentAccount()
            if (account != null) {
                val passkeyCredentials = account.passkeyCredentialIds.map { credentialId ->
                    PasskeyCredential(
                        credentialId = credentialId,
                        nickname = "Passkey", // In a real implementation, we'd get this from the API
                        deviceInfo = null,
                        createdAt = account.createdAt // Simplification
                    )
                }
                Result.success(passkeyCredentials)
            } else {
                Result.failure(IllegalStateException("Not authenticated"))
            }
        } catch (e: Exception) {
            Napier.e("Error getting passkey credentials", e)
            Result.failure(e)
        }
    }
    
    /**
     * Associates the user's local identity with their cloud account.
     *
     * @param userId The local user ID.
     * @param accountId The cloud account ID.
     * @return True if the association was successful.
     */
    override suspend fun associateUserIdentity(userId: Uuid, accountId: String): Result<Boolean> {
        // This would typically involve API calls to associate the user ID with the account
        // For now, we'll just store the association locally
        return try {
            secureStorage.putString(StorageKeys.USER_ID, userId.toString())
            Result.success(true)
        } catch (e: Exception) {
            Napier.e("Error associating user identity", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stores account and credentials information securely.
     */
    private suspend fun storeAccountAndCredentials(account: CloudAccount, credentials: AccountCredentials) {
        coroutineScope.launch {
            secureStorage.putString(StorageKeys.ACCESS_TOKEN, credentials.accessToken)
            secureStorage.putString(StorageKeys.REFRESH_TOKEN, credentials.refreshToken)
            secureStorage.putString(StorageKeys.ACCOUNT_ID, account.id.toString())
            secureStorage.putString(StorageKeys.ACCOUNT_USERNAME, account.username)
            secureStorage.putString(StorageKeys.ACCOUNT_DISPLAY_NAME, account.displayName)
            secureStorage.putString(StorageKeys.USER_ID, account.userId.toString())
            secureStorage.putString(StorageKeys.CREATED_AT, account.createdAt.toString())
            secureStorage.putString(StorageKeys.UPDATED_AT, account.updatedAt.toString())
            secureStorage.putString(StorageKeys.PASSKEY_IDS, account.passkeyCredentialIds.joinToString(",") { it.toString() })
        }.join() // Wait for the storage operations to complete
    }
    
    /**
     * Creates default device information from platform info.
     */
    private fun createDefaultDeviceInfo(): app.logdate.shared.model.DeviceInfoDto {
        val platformInfo = platformInfoProvider.getPlatformInfo()
        return app.logdate.shared.model.DeviceInfoDto(
            platform = platformInfo.platform,
            deviceName = platformInfo.deviceName,
            deviceType = mapDeviceType(platformInfo.deviceType).name
        )
    }
    
    /**
     * Maps platform-specific device type to domain model device type.
     */
    private fun mapDeviceType(platformType: String): DeviceType {
        return when (platformType.lowercase()) {
            "phone" -> DeviceType.MOBILE
            "tablet" -> DeviceType.TABLET
            "desktop" -> DeviceType.DESKTOP
            else -> DeviceType.UNKNOWN
        }
    }
}