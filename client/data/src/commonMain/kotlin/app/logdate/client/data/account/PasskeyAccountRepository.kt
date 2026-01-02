package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAuthenticationRequest
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyCredentialResponse
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Implementation of PasskeyAccountRepository for managing passkey-based account operations
 */
// Remove this incomplete class definition since we have the proper implementation below

/**
 * Default implementation of PasskeyAccountRepository
 */
class DefaultPasskeyAccountRepository(
    private val apiClient: PasskeyApiClientContract,
    private val passkeyManager: PasskeyManager,
    private val sessionStorage: SessionStorage,
    private val platformAccountManager: PlatformAccountManager,
    private val configRepository: LogDateConfigRepository,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
) : PasskeyAccountRepository {
    
    private val _currentAccount = MutableStateFlow<LogDateAccount?>(null)
    override val currentAccount: StateFlow<LogDateAccount?> = _currentAccount.asStateFlow()
    
    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    init {
        // Load existing session on initialization
        val existingSession = sessionStorage.getSession()
        if (existingSession != null) {
            _isAuthenticated.value = true
            // Note: We'll load account info lazily when needed
        }
    }
    
    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> {
        return try {
            val result = apiClient.checkUsernameAvailability(username)
            result.map { it.available }
        } catch (e: Exception) {
            Napier.e("Failed to check username availability", e)
            Result.failure(e)
        }
    }
    
    override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> {
        return try {
            // Step 1: Begin account creation
            val beginRequest = BeginAccountCreationRequest(
                username = request.username,
                displayName = request.displayName,
                bio = request.bio
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
            val completeRequest = CompleteAccountCreationRequest(
                sessionToken = beginData.sessionToken,
                credential = credential
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
                    accountId = completeData.account.id.toString()
                )
            )
            
            // Step 5: Add account to platform account manager
            val backendUrl = configRepository.getCurrentBackendUrl()
            val platformResult = platformAccountManager.addAccount(
                account = completeData.account,
                accessToken = completeData.tokens.accessToken,
                refreshToken = completeData.tokens.refreshToken,
                backendUrl = backendUrl
            )
            
            if (platformResult.isFailure) {
                Napier.w("Failed to add account to platform account manager", platformResult.exceptionOrNull())
                // Don't fail the entire operation since the account was created successfully
            }
            
            _currentAccount.value = completeData.account
            _isAuthenticated.value = true
            
            Napier.i("Account created successfully for user: ${completeData.account.username}")
            Result.success(completeData.account)
            
        } catch (e: Exception) {
            Napier.e("Failed to create account with passkey", e)
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
            val authOptions = PasskeyAuthenticationOptions(
                challenge = beginData.challenge,
                allowCredentials = beginData.allowCredentials.map { it.id },
                timeout = beginData.timeout
            )
            
            // Step 3: Authenticate with passkey
            val authResult = passkeyManager.authenticateWithPasskey(authOptions)
            if (authResult.isFailure) {
                return Result.failure(authResult.exceptionOrNull()!!)
            }
            
            val assertionJson = authResult.getOrThrow()
            val assertion = parseAssertionResponse(assertionJson)
            
            // Step 4: Complete authentication
            val completeRequest = CompleteAuthenticationRequest(
                credential = assertion,
                challenge = beginData.challenge
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
                    accountId = completeData.account.id.toString()
                )
            )
            
            // Step 6: Update tokens in platform account manager
            val platformTokenResult = platformAccountManager.updateTokens(
                username = completeData.account.username,
                accessToken = completeData.tokens.accessToken,
                refreshToken = completeData.tokens.refreshToken
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
            Napier.e("Failed to authenticate with passkey", e)
            Result.failure(e)
        }
    }
    
    override suspend fun signOut(): Result<Unit> {
        return try {
            val currentAccountValue = _currentAccount.value
            
            sessionStorage.clearSession()
            
            // Clear tokens from platform account manager but keep account info
            if (currentAccountValue != null) {
                val platformResult = platformAccountManager.updateTokens(
                    username = currentAccountValue.username,
                    accessToken = "",
                    refreshToken = ""
                )
                
                if (platformResult.isFailure) {
                    Napier.w("Failed to clear tokens in platform account manager", platformResult.exceptionOrNull())
                }
            }
            
            _currentAccount.value = null
            _isAuthenticated.value = false
            
            Napier.i("User signed out successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Failed to sign out", e)
            Result.failure(e)
        }
    }
    
    override suspend fun refreshAuthentication(): Result<Unit> {
        return try {
            val session = sessionStorage.getSession()
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
                val platformResult = platformAccountManager.updateTokens(
                    username = currentAccountValue.username,
                    accessToken = newAccessToken,
                    refreshToken = session.refreshToken
                )
                
                if (platformResult.isFailure) {
                    Napier.w("Failed to update tokens in platform account manager", platformResult.exceptionOrNull())
                }
            }
            
            Napier.i("Authentication refreshed successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Napier.e("Failed to refresh authentication", e)
            signOut() // Clear session on error
            Result.failure(e)
        }
    }
    
    override suspend fun getAccountInfo(): Result<LogDateAccount> {
        return try {
            val session = sessionStorage.getSession()
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
            Napier.e("Failed to get account info", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getCurrentAccount(): LogDateAccount? {
        return _currentAccount.value
    }
    
    override suspend fun deletePasskey(credentialId: String): Result<Unit> {
        return try {
            val session = sessionStorage.getSession()
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
            Napier.e("Failed to delete passkey: $credentialId", e)
            Result.failure(e)
        }
    }
    
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
