@file:Suppress("ktlint:standard:filename")

package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.RestoreCredentialError
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAuthenticationRequest
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyCredentialResponse
import app.logdate.shared.model.ServerCapability
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Default implementation of PasskeyAccountRepository for managing passkey-based account operations.
 */
class DefaultPasskeyAccountRepository(
    private val apiClient: PasskeyApiClientContract,
    private val passkeyManager: PasskeyManager,
    private val restoreCredentialManager: RestoreCredentialManager,
    private val sessionStorage: SessionStorage,
    private val platformAccountManager: PlatformAccountManager,
    private val configRepository: LogDateConfigRepository,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        },
) : PasskeyAccountRepository {
    private val _currentAccount = MutableStateFlow<LogDateAccount?>(null)
    override val currentAccount: StateFlow<LogDateAccount?> = _currentAccount.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        val existingSession = sessionStorage.getSession()
        if (existingSession != null) {
            _isAuthenticated.value = true
        }

        repositoryScope.launch {
            sessionStorage.getSessionFlow().collect { session ->
                _isAuthenticated.value = session != null
                if (session == null) {
                    _currentAccount.value = null
                }
            }
        }

        repositoryScope.launch {
            configRepository.backendUrl.drop(1).collect {
                _currentAccount.value = null
                _isAuthenticated.value = sessionStorage.getSession() != null
            }
        }
    }

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> =
        try {
            val result = apiClient.checkUsernameAvailability(username)
            result.map { it.available }
        } catch (e: Exception) {
            Napier.w("Failed to check username availability", e)
            Result.failure(e)
        }

    override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> {
        return try {
            // Step 1: Begin account creation
            val beginRequest =
                BeginAccountCreationRequest(
                    username = request.username,
                    displayName = request.displayName,
                    bio = request.bio,
                )

            val beginResult = apiClient.beginAccountCreation(beginRequest)
            if (beginResult.isFailure) {
                return Result.failure(beginResult.exceptionOrNull()!!)
            }

            val beginData = beginResult.getOrThrow()

            // Step 2: Create passkey using platform authenticator
            val registrationResult = passkeyManager.registerPasskey(beginData.registrationOptions)
            if (registrationResult.isFailure) {
                return Result.failure(registrationResult.exceptionOrNull()!!)
            }

            val credentialJson = registrationResult.getOrThrow()
            val credential = parseCredentialResponse(credentialJson)

            // Step 3: Complete account creation
            val completeRequest =
                CompleteAccountCreationRequest(
                    sessionToken = beginData.sessionToken,
                    credential = credential,
                )

            val completeResult = apiClient.completeAccountCreation(completeRequest)
            if (completeResult.isFailure) {
                return Result.failure(completeResult.exceptionOrNull()!!)
            }

            val completeData = completeResult.getOrThrow()

            // Step 4: Store session and account data
            sessionStorage.saveSession(
                UserSession(
                    accessToken = completeData.tokens.accessToken,
                    refreshToken = completeData.tokens.refreshToken,
                    accountId = completeData.account.id.toString(),
                ),
            )

            // Step 5: Add account to platform account manager
            val backendUrl = configRepository.getCurrentBackendUrl()
            val platformResult =
                platformAccountManager.addAccount(
                    account = completeData.account,
                    accessToken = completeData.tokens.accessToken,
                    refreshToken = completeData.tokens.refreshToken,
                    backendUrl = backendUrl,
                )

            if (platformResult.isFailure) {
                Napier.w("Failed to add account to platform account manager", platformResult.exceptionOrNull())
                // Don't fail the entire operation since the account was created successfully
            }

            _currentAccount.value = completeData.account
            _isAuthenticated.value = true

            // Attempt to create a restore key for seamless re-authentication after device restore.
            // This is non-fatal — if E2EE backup is unavailable, we silently skip.
            createRestoreKey()

            Napier.i("Account created successfully for user: ${completeData.account.username}")
            Result.success(completeData.account)
        } catch (e: Exception) {
            Napier.w("Failed to create account with passkey", e)
            Result.failure(e)
        }
    }

    override suspend fun authenticateWithPasskey(username: String?): Result<LogDateAccount> {
        return try {
            // Step 1: Begin authentication
            val beginRequest = BeginAuthenticationRequest(username = username)
            val beginResult = apiClient.beginAuthentication(beginRequest)

            if (beginResult.isFailure) {
                return Result.failure(beginResult.exceptionOrNull()!!)
            }

            val beginData = beginResult.getOrThrow()

            // Step 2: Convert to PasskeyAuthenticationOptions
            val authOptions =
                PasskeyAuthenticationOptions(
                    challenge = beginData.challenge,
                    rpId = beginData.rpId,
                    allowCredentials = beginData.allowCredentials.map { it.id },
                    timeout = beginData.timeout,
                )

            // Step 3: Authenticate with passkey
            val authResult = passkeyManager.authenticateWithPasskey(authOptions)
            if (authResult.isFailure) {
                return Result.failure(authResult.exceptionOrNull()!!)
            }

            val assertionJson = authResult.getOrThrow()
            val assertion = parseAssertionResponse(assertionJson)

            // Step 4: Complete authentication
            val completeRequest =
                CompleteAuthenticationRequest(
                    credential = assertion,
                    challenge = beginData.challenge,
                )

            val completeResult = apiClient.completeAuthentication(completeRequest)
            if (completeResult.isFailure) {
                return Result.failure(completeResult.exceptionOrNull()!!)
            }

            val completeData = completeResult.getOrThrow()

            // Step 5: Store session and account data
            sessionStorage.saveSession(
                UserSession(
                    accessToken = completeData.tokens.accessToken,
                    refreshToken = completeData.tokens.refreshToken,
                    accountId = completeData.account.id.toString(),
                ),
            )

            // Step 6: Update tokens in platform account manager
            val platformTokenResult =
                platformAccountManager.updateTokens(
                    username = completeData.account.username,
                    backendUrl = configRepository.getCurrentBackendUrl(),
                    accessToken = completeData.tokens.accessToken,
                    refreshToken = completeData.tokens.refreshToken,
                )

            if (platformTokenResult.isFailure) {
                Napier.w("Failed to update tokens in platform account manager", platformTokenResult.exceptionOrNull())
                // Don't fail the entire operation since authentication was successful
            }

            _currentAccount.value = completeData.account
            _isAuthenticated.value = true

            Napier.i("Authentication successful for user: ${completeData.account.username}")
            Result.success(completeData.account)
        } catch (e: Exception) {
            Napier.w("Failed to authenticate with passkey", e)
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> =
        try {
            val currentAccountValue = _currentAccount.value

            sessionStorage.clearSession()

            // Clear tokens from platform account manager but keep account info
            if (currentAccountValue != null) {
                val platformResult =
                    platformAccountManager.updateTokens(
                        username = currentAccountValue.username,
                        backendUrl = configRepository.getCurrentBackendUrl(),
                        accessToken = "",
                        refreshToken = "",
                    )

                if (platformResult.isFailure) {
                    Napier.w("Failed to clear tokens in platform account manager", platformResult.exceptionOrNull())
                }
            }

            _currentAccount.value = null
            _isAuthenticated.value = false

            // Clear restore credential on sign-out — best effort, non-fatal.
            deleteRestoreKey()

            Napier.i("User signed out successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.w("Failed to sign out", e)
            Result.failure(e)
        }

    override suspend fun refreshAuthentication(): Result<Unit> {
        return try {
            val session =
                sessionStorage.getSession()
                    ?: return Result.failure(Exception("No active session"))

            val refreshResult = apiClient.refreshToken(session.refreshToken)
            if (refreshResult.isFailure) {
                // If refresh fails, clear the session
                signOut()
                return Result.failure(refreshResult.exceptionOrNull()!!)
            }

            val newAccessToken = refreshResult.getOrThrow()

            // Update session with new access token
            val updatedSession = session.copy(accessToken = newAccessToken)
            sessionStorage.saveSession(updatedSession)

            // Update access token in platform account manager
            val currentAccountValue = _currentAccount.value
            if (currentAccountValue != null) {
                val platformResult =
                    platformAccountManager.updateTokens(
                        username = currentAccountValue.username,
                        backendUrl = configRepository.getCurrentBackendUrl(),
                        accessToken = newAccessToken,
                        refreshToken = session.refreshToken,
                    )

                if (platformResult.isFailure) {
                    Napier.w("Failed to update tokens in platform account manager", platformResult.exceptionOrNull())
                }
            }

            Napier.i("Authentication refreshed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.w("Failed to refresh authentication", e)
            signOut() // Clear session on error
            Result.failure(e)
        }
    }

    override suspend fun getAccountInfo(): Result<LogDateAccount> {
        return try {
            val session =
                sessionStorage.getSession()
                    ?: return Result.failure(Exception("No active session"))

            val result = apiClient.getAccountInfo(session.accessToken)
            if (result.isSuccess) {
                val account = result.getOrThrow()
                _currentAccount.value = account
                return Result.success(account)
            } else {
                // Try refreshing token and retry once
                val refreshResult = refreshAuthentication()
                if (refreshResult.isSuccess) {
                    val updatedSession = sessionStorage.getSession()!!
                    val retryResult = apiClient.getAccountInfo(updatedSession.accessToken)
                    if (retryResult.isSuccess) {
                        val account = retryResult.getOrThrow()
                        _currentAccount.value = account
                        return Result.success(account)
                    }
                }
                return result
            }
        } catch (e: Exception) {
            Napier.w("Failed to get account info", e)
            Result.failure(e)
        }
    }

    override suspend fun getCurrentAccount(): LogDateAccount? = _currentAccount.value

    override suspend fun deletePasskey(credentialId: String): Result<Unit> {
        return try {
            val session =
                sessionStorage.getSession()
                    ?: return Result.failure(Exception("No active session"))

            val result = apiClient.deletePasskey(session.accessToken, credentialId)
            if (result.isSuccess) {
                Napier.i("Passkey deleted successfully: $credentialId")
                return Result.success(Unit)
            } else {
                // Try refreshing token and retry once
                val refreshResult = refreshAuthentication()
                if (refreshResult.isSuccess) {
                    val updatedSession = sessionStorage.getSession()!!
                    val retryResult = apiClient.deletePasskey(updatedSession.accessToken, credentialId)
                    if (retryResult.isSuccess) {
                        Napier.i("Passkey deleted successfully after token refresh: $credentialId")
                        return Result.success(Unit)
                    }
                }
                return result
            }
        } catch (e: Exception) {
            Napier.w("Failed to delete passkey: $credentialId", e)
            Result.failure(e)
        }
    }

    override suspend fun createRestoreKey(): Result<Unit> {
        val session = sessionStorage.getSession() ?: return Result.failure(Exception("No active session"))
        return try {
            val optionsResult = apiClient.beginRestoreKeyRegistration(session.accessToken)
            if (optionsResult.isFailure) {
                Napier.w("Could not begin restore key registration — server may not support it", optionsResult.exceptionOrNull())
                return Result.success(Unit)
            }
            val options = optionsResult.getOrThrow()

            val credentialResult = restoreCredentialManager.createRestoreKey(options)
            if (credentialResult.isFailure) {
                val error = credentialResult.exceptionOrNull()
                if (error is RestoreCredentialError.BackupUnavailable) {
                    Napier.i("Restore key skipped — device does not support E2EE backup")
                    return Result.success(Unit)
                }
                Napier.w("Restore key creation failed (non-fatal)", error)
                return Result.success(Unit)
            }

            val completeResult =
                apiClient.completeRestoreKeyRegistration(
                    accessToken = session.accessToken,
                    credentialJson = credentialResult.getOrThrow(),
                    challenge = options.challenge,
                )
            if (completeResult.isFailure) {
                Napier.w("Could not register restore key with server (non-fatal)", completeResult.exceptionOrNull())
            } else {
                Napier.i("Restore key registered successfully")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.w("Restore key creation encountered an unexpected error (non-fatal)", e)
            Result.success(Unit)
        }
    }

    override suspend fun signInWithRestoreKey(): Result<LogDateAccount> {
        val serverDescriptor = configRepository.getCurrentServerDescriptor()
        if (serverDescriptor != null && !serverDescriptor.hasCapability(ServerCapability.AUTH_PASSKEY)) {
            return Result.failure(Exception("Server does not support passkey authentication"))
        }

        return try {
            val beginResult = apiClient.beginRestoreSignIn()
            if (beginResult.isFailure) {
                return Result.failure(beginResult.exceptionOrNull()!!)
            }
            val beginData = beginResult.getOrThrow()

            val authOptions =
                PasskeyAuthenticationOptions(
                    challenge = beginData.challenge,
                    rpId = beginData.rpId,
                    allowCredentials = beginData.allowCredentials.map { it.id },
                    timeout = beginData.timeout,
                )

            val credentialResult = restoreCredentialManager.getRestoreCredential(authOptions)
            if (credentialResult.isFailure) {
                return Result.failure(credentialResult.exceptionOrNull()!!)
            }

            val assertion = parseAssertionResponse(credentialResult.getOrThrow())
            val completeRequest =
                CompleteAuthenticationRequest(
                    credential = assertion,
                    challenge = beginData.challenge,
                )

            val completeResult = apiClient.completeRestoreSignIn(completeRequest)
            if (completeResult.isFailure) {
                return Result.failure(completeResult.exceptionOrNull()!!)
            }

            val completeData = completeResult.getOrThrow()
            sessionStorage.saveSession(
                UserSession(
                    accessToken = completeData.tokens.accessToken,
                    refreshToken = completeData.tokens.refreshToken,
                    accountId = completeData.account.id.toString(),
                ),
            )

            platformAccountManager
                .updateTokens(
                    username = completeData.account.username,
                    backendUrl = configRepository.getCurrentBackendUrl(),
                    accessToken = completeData.tokens.accessToken,
                    refreshToken = completeData.tokens.refreshToken,
                ).onFailure { Napier.w("Failed to update platform account tokens after restore sign-in", it) }

            _currentAccount.value = completeData.account
            _isAuthenticated.value = true

            // Re-register a restore credential for this device so future restores work.
            // The old credential was consumed server-side; this issues a fresh one.
            createRestoreKey()

            Napier.i("Restore sign-in successful for user: ${completeData.account.username}")
            Result.success(completeData.account)
        } catch (e: Exception) {
            Napier.i("Restore sign-in failed: ${e.message ?: "unknown error"}")
            Result.failure(e)
        }
    }

    override suspend fun deleteRestoreKey(): Result<Unit> =
        restoreCredentialManager
            .clearRestoreCredential()
            .onFailure { Napier.w("Failed to clear restore credential (non-fatal)", it) }
            .let { Result.success(Unit) }

    private fun parseCredentialResponse(credentialJson: String): PasskeyCredentialResponse {
        // Parse the WebAuthn credential response from the platform
        // This format depends on the platform implementation
        return json.decodeFromString(PasskeyCredentialResponse.serializer(), credentialJson)
    }

    private fun parseAssertionResponse(assertionJson: String): PasskeyAssertionResponse {
        // Parse the WebAuthn assertion response from the platform
        return json.decodeFromString(PasskeyAssertionResponse.serializer(), assertionJson)
    }
}
