package app.logdate.client.sync.cloud.account

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.AccountCredentials
import app.logdate.shared.model.AuthenticationResult
import app.logdate.shared.model.BeginAccountCreationResult
import app.logdate.shared.model.CloudAccount
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.DeviceInfo
import app.logdate.shared.model.PasskeyCredential
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Default implementation of [CloudAccountRepository].
 *
 * This implementation uses the [CloudApiClient] for network operations and
 * securely stores credentials using [KeyValueStorage].
 *
 * @param apiClient The client for LogDate Cloud API communication.
 * @param secureStorage The storage for secure data like tokens.
 */
class DefaultCloudAccountRepository(
    private val apiClient: CloudApiClient,
    private val secureStorage: KeyValueStorage,
    private val configRepository: LogDateConfigRepository = DefaultLogDateConfigRepository(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
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
        const val ACCOUNT_DID = "cloud_account_did"
        const val ACCOUNT_HANDLE = "cloud_account_handle"
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
        coroutineScope.launch {
            configRepository.backendUrl.collect {
                loadStoredAccount()
            }
        }
    }

    /**
     * Loads account information from secure storage if available.
     */
    private suspend fun loadStoredAccount() {
        try {
            val backendUrl = configRepository.getCurrentBackendUrl()
            migrateLegacyKeysIfNeeded(backendUrl)

            // Check if we have stored credentials
            val accessToken = secureStorage.getString(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl))
            val accountId = secureStorage.getString(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl))
            val username = secureStorage.getString(scopedKey(StorageKeys.ACCOUNT_USERNAME, backendUrl))
            val displayName = secureStorage.getString(scopedKey(StorageKeys.ACCOUNT_DISPLAY_NAME, backendUrl))
            val did = secureStorage.getString(scopedKey(StorageKeys.ACCOUNT_DID, backendUrl))
            val handle = secureStorage.getString(scopedKey(StorageKeys.ACCOUNT_HANDLE, backendUrl))
            val userIdString = secureStorage.getString(scopedKey(StorageKeys.USER_ID, backendUrl))
            val createdAtString = secureStorage.getString(scopedKey(StorageKeys.CREATED_AT, backendUrl))
            val updatedAtString = secureStorage.getString(scopedKey(StorageKeys.UPDATED_AT, backendUrl))
            val passkeyIdsString = secureStorage.getString(scopedKey(StorageKeys.PASSKEY_IDS, backendUrl))

            if (accessToken != null &&
                accountId != null &&
                username != null &&
                displayName != null &&
                userIdString != null &&
                createdAtString != null &&
                updatedAtString != null
            ) {
                val userId = Uuid.parse(userIdString)
                val createdAt = Instant.parse(createdAtString)
                val updatedAt = Instant.parse(updatedAtString)
                val passkeyIds =
                    passkeyIdsString
                        ?.split(",")
                        ?.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
                        ?: emptyList()

                // Convert String IDs to UUIDs
                val account =
                    CloudAccount(
                        id = Uuid.parse(accountId),
                        username = username,
                        displayName = displayName,
                        did = did,
                        handle = handle,
                        userId = userId,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        passkeyCredentialIds = passkeyIds,
                    )

                accountFlow.value = account
                Napier.d("Loaded stored account: $username")
            } else {
                accountFlow.value = null
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
    override suspend fun isUsernameAvailable(username: String): Result<Boolean> =
        try {
            // Using the RESTful approach
            val result = apiClient.checkUsernameAvailability(username)

            result.map { response ->
                response.available
            }
        } catch (e: Exception) {
            Napier.e("Error checking username availability", e)
            Result.failure(e)
        }

    /**
     * Begins the account creation process.
     *
     * @param username The desired username.
     * @param displayName The user's display name.
     * @param deviceInfo Optional device information.
     * @return A session token and challenge for passkey creation.
     */
    @Suppress("UNUSED_PARAMETER")
    override suspend fun beginAccountCreation(
        username: String,
        displayName: String,
        deviceInfo: DeviceInfo?,
    ): Result<BeginAccountCreationResult> =
        try {
            // Device metadata is currently not part of the auth v1 begin-signup contract.
            // Keep the parameter for domain interface compatibility until the API adds support.
            val request =
                app.logdate.shared.model.BeginAccountCreationRequest(
                    username = username,
                    displayName = displayName,
                    bio = null, // Optional bio not supported in domain model yet
                )

            val result = apiClient.beginAccountCreation(request)

            result.map { response ->
                val registrationUserId = decodeWebAuthnUserId(response.data.registrationOptions.user.id) ?: Uuid.random()
                BeginAccountCreationResult(
                    sessionToken = response.data.sessionToken,
                    challenge = response.data.registrationOptions.challenge,
                    rpId = response.data.registrationOptions.rpId,
                    rpName = response.data.registrationOptions.rpName,
                    userId = registrationUserId,
                    username = response.data.registrationOptions.user.name,
                    displayName = response.data.registrationOptions.user.displayName,
                    timeout = response.data.registrationOptions.timeout,
                )
            }
        } catch (e: Exception) {
            Napier.e("Error beginning account creation", e)
            Result.failure(e)
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
        attestationObject: String,
    ): Result<AuthenticationResult> =
        try {
            val request =
                app.logdate.shared.model.CompleteAccountCreationRequest(
                    sessionToken = sessionToken,
                    credential =
                        app.logdate.shared.model.PasskeyCredentialResponse(
                            id = credentialId,
                            rawId = credentialId, // In a real implementation, these would be different
                            response =
                                app.logdate.shared.model.PasskeyAuthenticatorResponse(
                                    clientDataJSON = clientDataJSON,
                                    attestationObject = attestationObject,
                                ),
                            type = "public-key",
                        ),
                )

            val result = apiClient.completeAccountCreation(request)

            result.map { response ->
                if (response.success) {
                    val accountDto = response.data.account
                    val tokensDto = response.data.tokens

                    // Create the account and credentials
                    val account =
                        CloudAccount(
                            id = accountDto.id,
                            username = accountDto.username,
                            displayName = accountDto.displayName,
                            did = accountDto.did,
                            handle = accountDto.handle,
                            userId = accountDto.id,
                            createdAt = accountDto.createdAt,
                            updatedAt = accountDto.updatedAt,
                            passkeyCredentialIds =
                                accountDto.passkeyCredentialIds.mapNotNull {
                                    runCatching { Uuid.parse(it) }.getOrNull()
                                },
                        )

                    val credentials =
                        AccountCredentials(
                            accessToken = tokensDto.accessToken,
                            refreshToken = tokensDto.refreshToken,
                            expiresIn = 3600, // Default to 1 hour if not specified
                        )

                    // Store the account and credentials
                    storeAccountAndCredentials(account, credentials)

                    // Update the current account flow
                    accountFlow.value = account

                    AuthenticationResult.Success(account, credentials)
                } else {
                    AuthenticationResult.Error(
                        errorCode = "ACCOUNT_CREATION_FAILED",
                        message = "Failed to create account",
                    )
                }
            }
        } catch (e: Exception) {
            Napier.e("Error completing account creation", e)
            Result.failure(e)
        }

    /**
     * Gets the currently authenticated account.
     *
     * @return The current account, or null if not authenticated.
     */
    override suspend fun getCurrentAccount(): CloudAccount? = accountFlow.value

    /**
     * Observes the current authentication state.
     *
     * @return A flow emitting the current account whenever it changes.
     */
    override fun observeCurrentAccount(): Flow<CloudAccount?> = accountFlow.asStateFlow()

    /**
     * Refreshes the access token using a refresh token.
     *
     * @param refreshToken The refresh token.
     * @return The new access token if successful.
     */
    override suspend fun refreshAccessToken(refreshToken: String): Result<String> =
        try {
            val result = apiClient.refreshAccessToken(refreshToken)

            result.onSuccess { newToken ->
                // Update the stored token
                secureStorage.putString(scopedKey(StorageKeys.ACCESS_TOKEN, configRepository.getCurrentBackendUrl()), newToken)
            }

            result
        } catch (e: Exception) {
            Napier.e("Error refreshing access token", e)
            Result.failure(e)
        }

    /**
     * Signs out the current user.
     *
     * @return True if the sign-out was successful.
     */
    override suspend fun signOut(): Result<Boolean> =
        try {
            val backendUrl = configRepository.getCurrentBackendUrl()
            // Clear all stored account information
            secureStorage.remove(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.REFRESH_TOKEN, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.ACCOUNT_USERNAME, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.ACCOUNT_DISPLAY_NAME, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.ACCOUNT_DID, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.ACCOUNT_HANDLE, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.USER_ID, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.CREATED_AT, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.UPDATED_AT, backendUrl))
            secureStorage.remove(scopedKey(StorageKeys.PASSKEY_IDS, backendUrl))

            // Clear the current account
            accountFlow.value = null

            Result.success(true)
        } catch (e: Exception) {
            Napier.e("Error signing out", e)
            Result.failure(e)
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
                val passkeyCredentials =
                    account.passkeyCredentialIds.map { credentialId ->
                        PasskeyCredential(
                            credentialId = credentialId,
                            nickname = "Passkey", // In a real implementation, we'd get this from the API
                            deviceInfo = null,
                            createdAt = account.createdAt, // Simplification
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
    override suspend fun associateUserIdentity(
        userId: Uuid,
        accountId: String,
    ): Result<Boolean> {
        // This would typically involve API calls to associate the user ID with the account
        // For now, we'll just store the association locally
        return try {
            secureStorage.putString(scopedKey(StorageKeys.USER_ID, configRepository.getCurrentBackendUrl()), userId.toString())
            Result.success(true)
        } catch (e: Exception) {
            Napier.e("Error associating user identity", e)
            Result.failure(e)
        }
    }

    /**
     * Stores account and credentials information securely.
     */
    private suspend fun storeAccountAndCredentials(
        account: CloudAccount,
        credentials: AccountCredentials,
    ) {
        val backendUrl = configRepository.getCurrentBackendUrl()
        secureStorage.putString(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl), credentials.accessToken)
        secureStorage.putString(scopedKey(StorageKeys.REFRESH_TOKEN, backendUrl), credentials.refreshToken)
        secureStorage.putString(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl), account.id.toString())
        secureStorage.putString(scopedKey(StorageKeys.ACCOUNT_USERNAME, backendUrl), account.username)
        secureStorage.putString(scopedKey(StorageKeys.ACCOUNT_DISPLAY_NAME, backendUrl), account.displayName)
        account.did?.let { secureStorage.putString(scopedKey(StorageKeys.ACCOUNT_DID, backendUrl), it) }
            ?: secureStorage.remove(scopedKey(StorageKeys.ACCOUNT_DID, backendUrl))
        account.handle?.let { secureStorage.putString(scopedKey(StorageKeys.ACCOUNT_HANDLE, backendUrl), it) }
            ?: secureStorage.remove(scopedKey(StorageKeys.ACCOUNT_HANDLE, backendUrl))
        secureStorage.putString(scopedKey(StorageKeys.USER_ID, backendUrl), account.userId.toString())
        secureStorage.putString(scopedKey(StorageKeys.CREATED_AT, backendUrl), account.createdAt.toString())
        secureStorage.putString(scopedKey(StorageKeys.UPDATED_AT, backendUrl), account.updatedAt.toString())
        secureStorage.putString(
            scopedKey(StorageKeys.PASSKEY_IDS, backendUrl),
            account.passkeyCredentialIds.joinToString(",") { it.toString() },
        )
    }

    private fun decodeWebAuthnUserId(encodedUserId: String): Uuid? =
        runCatching {
            val raw = Base64.decode(encodedUserId)
            Uuid.parse(raw.decodeToString())
        }.getOrNull()

    private suspend fun migrateLegacyKeysIfNeeded(backendUrl: String) {
        if (secureStorage.getString(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl)) != null) {
            return
        }

        val legacyAccountId = secureStorage.getString(StorageKeys.ACCOUNT_ID) ?: return
        copyLegacyValue(
            sourceKey = StorageKeys.ACCESS_TOKEN,
            destinationKey = scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl),
        )
        copyLegacyValue(
            sourceKey = StorageKeys.REFRESH_TOKEN,
            destinationKey = scopedKey(StorageKeys.REFRESH_TOKEN, backendUrl),
        )
        secureStorage.putString(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl), legacyAccountId)
        copyLegacyValue(
            sourceKey = StorageKeys.ACCOUNT_USERNAME,
            destinationKey = scopedKey(StorageKeys.ACCOUNT_USERNAME, backendUrl),
        )
        copyLegacyValue(
            sourceKey = StorageKeys.ACCOUNT_DISPLAY_NAME,
            destinationKey = scopedKey(StorageKeys.ACCOUNT_DISPLAY_NAME, backendUrl),
        )
        copyLegacyValue(
            sourceKey = StorageKeys.ACCOUNT_DID,
            destinationKey = scopedKey(StorageKeys.ACCOUNT_DID, backendUrl),
        )
        copyLegacyValue(
            sourceKey = StorageKeys.ACCOUNT_HANDLE,
            destinationKey = scopedKey(StorageKeys.ACCOUNT_HANDLE, backendUrl),
        )
        copyLegacyValue(
            sourceKey = StorageKeys.USER_ID,
            destinationKey = scopedKey(StorageKeys.USER_ID, backendUrl),
        )
        copyLegacyValue(
            sourceKey = StorageKeys.CREATED_AT,
            destinationKey = scopedKey(StorageKeys.CREATED_AT, backendUrl),
        )
        copyLegacyValue(
            sourceKey = StorageKeys.UPDATED_AT,
            destinationKey = scopedKey(StorageKeys.UPDATED_AT, backendUrl),
        )
        copyLegacyValue(
            sourceKey = StorageKeys.PASSKEY_IDS,
            destinationKey = scopedKey(StorageKeys.PASSKEY_IDS, backendUrl),
        )

        listOf(
            StorageKeys.ACCESS_TOKEN,
            StorageKeys.REFRESH_TOKEN,
            StorageKeys.ACCOUNT_ID,
            StorageKeys.ACCOUNT_USERNAME,
            StorageKeys.ACCOUNT_DISPLAY_NAME,
            StorageKeys.ACCOUNT_DID,
            StorageKeys.ACCOUNT_HANDLE,
            StorageKeys.USER_ID,
            StorageKeys.CREATED_AT,
            StorageKeys.UPDATED_AT,
            StorageKeys.PASSKEY_IDS,
        ).forEach { key ->
            secureStorage.remove(key)
        }
    }

    private suspend fun copyLegacyValue(
        sourceKey: String,
        destinationKey: String,
    ) {
        secureStorage.getString(sourceKey)?.let { value ->
            secureStorage.putString(destinationKey, value)
        }
    }

    private fun scopedKey(
        baseKey: String,
        backendUrl: String,
    ): String = "${baseKey}_${backendUrl.trim().removePrefix("https://").removePrefix("http://").replace(Regex("[^A-Za-z0-9]"), "_")}"
}
