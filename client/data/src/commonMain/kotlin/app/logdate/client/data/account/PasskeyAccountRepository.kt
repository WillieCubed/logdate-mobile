@file:Suppress("ktlint:standard:filename")

package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.GoogleSignInManager
import app.logdate.client.permissions.NoOpGoogleSignInManager
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.RestoreCredentialError
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.LocalDataAdoptionRequiredException
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAuthenticationRequest
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyCredentialResponse
import app.logdate.shared.model.PasskeyInfo
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerProtocolFeature
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
    private val canonicalOwnerProvider: CanonicalOwnerProvider,
    private val hasLocalData: suspend () -> Boolean,
    private val googleSignInManager: GoogleSignInManager = NoOpGoogleSignInManager(),
    private val serverClientId: String = "",
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
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

    init {
        repositoryScope.launch {
            sessionStorage.getSessionFlow().collect { session ->
                if (session != null && !sessionBelongsToCanonicalOwner(session)) {
                    Napier.w("Discarding credentials for a different LogDate identity")
                    sessionStorage.clearSession()
                    _isAuthenticated.value = false
                    _currentAccount.value = null
                } else {
                    _isAuthenticated.value = session != null
                    if (session == null) {
                        _currentAccount.value = null
                    }
                }
            }
        }

        repositoryScope.launch {
            configRepository.backendUrl.drop(1).collect {
                _currentAccount.value = null
                val session = sessionStorage.getSession()
                _isAuthenticated.value = session != null && sessionBelongsToCanonicalOwner(session)
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
            requireCanonicalOwnerBinding()
            // Step 1: Begin account creation
            val canonicalOwnerId = canonicalOwnerProvider.getCanonicalOwnerId()
            val beginRequest =
                BeginAccountCreationRequest(
                    username = request.username,
                    displayName = request.displayName,
                    bio = request.bio,
                    requestedOwnerId = canonicalOwnerId,
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
            if (completeData.account.id.toString() != canonicalOwnerId) {
                return Result.failure(CanonicalOwnerMismatchException())
            }

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
            // Logged at WARN on failure so operators see the broken-recovery path; non-fatal
            // for account creation itself. UI surfaces should observe a future
            // restoreKeyStatus signal and prompt the user to retry from settings.
            createRestoreKey().onFailure { error ->
                Napier.w("Account created but restore key registration failed — user has no recovery path", error)
            }

            Napier.i("Account created successfully for user: ${completeData.account.username}")
            Result.success(completeData.account)
        } catch (e: Exception) {
            Napier.w("Failed to create account with passkey", e)
            Result.failure(e)
        }
    }

    private class CanonicalOwnerMismatchException :
        IllegalStateException(
            "LogDate Cloud returned an account that does not match this installation's identity",
        )

    override suspend fun authenticateWithPasskey(
        username: String?,
        adoptLocalData: Boolean,
    ): Result<LogDateAccount> {
        return try {
            requireCanonicalOwnerBinding()
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
            when (ownerBindingFor(completeData.account, adoptLocalData)) {
                OwnerBinding.ALLOWED -> Unit
                OwnerBinding.NEEDS_LOCAL_DATA_CONSENT ->
                    return Result.failure(LocalDataAdoptionRequiredException())
                OwnerBinding.REFUSED ->
                    return Result.failure(CanonicalOwnerMismatchException())
            }

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

    override suspend fun signUpWithGoogle(
        username: String?,
        displayName: String?,
    ): Result<LogDateAccount> {
        return try {
            requireCanonicalOwnerBinding()
            val canonicalOwnerId = canonicalOwnerProvider.getCanonicalOwnerId()
            val tokenResult = googleSignInManager.getGoogleIdToken(serverClientId)
            if (tokenResult.isFailure) {
                return Result.failure(tokenResult.exceptionOrNull()!!)
            }

            val createResult =
                apiClient.signUpWithGoogle(
                    idToken = tokenResult.getOrThrow(),
                    username = username,
                    displayName = displayName,
                    requestedOwnerId = canonicalOwnerId,
                )
            if (createResult.isFailure) {
                return Result.failure(createResult.exceptionOrNull()!!)
            }

            val data = createResult.getOrThrow()
            if (data.account.id.toString() != canonicalOwnerId) {
                return Result.failure(CanonicalOwnerMismatchException())
            }
            persistAuthenticatedSession(data.account, data.tokens, isNewAccount = true)

            createRestoreKey().onFailure { error ->
                Napier.w("Google account created but restore key registration failed — user has no recovery path", error)
            }

            Napier.i("Account created with Google for user: ${data.account.username}")
            Result.success(data.account)
        } catch (e: Exception) {
            Napier.w("Failed to sign up with Google", e)
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(): Result<LogDateAccount> {
        return try {
            requireCanonicalOwnerBinding()
            val tokenResult = googleSignInManager.getGoogleIdToken(serverClientId)
            if (tokenResult.isFailure) {
                return Result.failure(tokenResult.exceptionOrNull()!!)
            }

            val authResult = apiClient.signInWithGoogle(idToken = tokenResult.getOrThrow())
            if (authResult.isFailure) {
                return Result.failure(authResult.exceptionOrNull()!!)
            }

            val data = authResult.getOrThrow()
            if (!belongsToCanonicalOwner(data.account)) {
                return Result.failure(CanonicalOwnerMismatchException())
            }
            persistAuthenticatedSession(data.account, data.tokens, isNewAccount = false)

            Napier.i("Authentication with Google successful for user: ${data.account.username}")
            Result.success(data.account)
        } catch (e: Exception) {
            Napier.w("Failed to sign in with Google", e)
            Result.failure(e)
        }
    }

    /**
     * Persists tokens + account after a successful authentication and updates the platform account
     * manager. [isNewAccount] selects between registering a new platform account and refreshing the
     * tokens of an existing one. Platform-account-manager failures are non-fatal.
     */
    private suspend fun persistAuthenticatedSession(
        account: LogDateAccount,
        tokens: AccountTokens,
        isNewAccount: Boolean,
    ) {
        sessionStorage.saveSession(
            UserSession(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                accountId = account.id.toString(),
            ),
        )

        val backendUrl = configRepository.getCurrentBackendUrl()
        val platformResult =
            if (isNewAccount) {
                platformAccountManager.addAccount(
                    account = account,
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    backendUrl = backendUrl,
                )
            } else {
                platformAccountManager.updateTokens(
                    username = account.username,
                    backendUrl = backendUrl,
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                )
            }
        if (platformResult.isFailure) {
            Napier.w("Failed to update platform account manager after Google auth", platformResult.exceptionOrNull())
        }

        _currentAccount.value = account
        _isAuthenticated.value = true
    }

    private enum class OwnerBinding { ALLOWED, NEEDS_LOCAL_DATA_CONSENT, REFUSED }

    /**
     * An installation already claimed by an account stays with it. One that no account has claimed
     * can be adopted -- but if it holds entries written offline, those entries become part of the
     * account, so the user is asked first rather than told afterwards.
     */
    private suspend fun ownerBindingFor(
        account: LogDateAccount,
        adoptLocalData: Boolean,
    ): OwnerBinding {
        if (canonicalOwnerProvider.hasBoundOwner()) {
            val matches = account.id.toString() == canonicalOwnerProvider.getCanonicalOwnerId()
            return if (matches) OwnerBinding.ALLOWED else OwnerBinding.REFUSED
        }

        // A probe that fails tells us nothing about what is on the device, so ask rather than
        // silently adopt.
        if (!adoptLocalData && runCatching { hasLocalData() }.getOrDefault(true)) {
            return OwnerBinding.NEEDS_LOCAL_DATA_CONSENT
        }

        return if (canonicalOwnerProvider.adoptRemoteOwnerIfUninitialized(account.id.toString())) {
            OwnerBinding.ALLOWED
        } else {
            OwnerBinding.REFUSED
        }
    }

    private suspend fun belongsToCanonicalOwner(account: LogDateAccount): Boolean =
        ownerBindingFor(account, adoptLocalData = false) == OwnerBinding.ALLOWED

    private suspend fun sessionBelongsToCanonicalOwner(session: UserSession): Boolean =
        runCatching { session.accountId == canonicalOwnerProvider.getCanonicalOwnerId() }.getOrDefault(false)

    override suspend fun signOut(): Result<Unit> =
        try {
            val currentAccountValue = _currentAccount.value
            val session = sessionStorage.getSession()

            session?.let {
                apiClient.logout(it.refreshToken).onFailure { error ->
                    Napier.w("Remote logout failed; local credentials will still be cleared", error)
                }
            }

            sessionStorage.clearSession()

            // A LogDate installation has one canonical identity. Remove the platform account
            // rather than leaving an origin-scoped blank account that can later be mistaken for
            // another selectable identity.
            if (currentAccountValue != null) {
                val platformResult =
                    platformAccountManager.removeAccount(
                        username = currentAccountValue.username,
                        backendUrl = configRepository.getCurrentBackendUrl(),
                    )

                if (platformResult.isFailure) {
                    Napier.w("Failed to remove platform account on sign-out", platformResult.exceptionOrNull())
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

    override suspend fun listPasskeys(): Result<List<PasskeyInfo>> {
        val session =
            sessionStorage.getSession()
                ?: return Result.failure(Exception("No active session"))

        val result = apiClient.listPasskeys(session.accessToken)
        if (result.isSuccess) return result

        // Same refresh-and-retry as the other authenticated calls: an expired token should cost
        // the user a refresh, not an empty list that reads as "you have no passkeys".
        val refreshResult = refreshAuthentication()
        if (!refreshResult.isSuccess) return result
        val updatedSession = sessionStorage.getSession() ?: return result
        return apiClient.listPasskeys(updatedSession.accessToken)
    }

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
        if (sessionStorage.getSession() == null) {
            return Result.failure(Exception("No active session"))
        }
        return try {
            val optionsResult = withFreshAccessToken { accessToken -> apiClient.beginRestoreKeyRegistration(accessToken) }
            if (optionsResult.isFailure) {
                val cause = optionsResult.exceptionOrNull()
                Napier.w("Restore key registration failed: server rejected /auth/restore/register/begin", cause)
                return Result.failure(cause ?: Exception("Restore key begin failed"))
            }
            val options = optionsResult.getOrThrow()

            val credentialResult = restoreCredentialManager.createRestoreKey(options)
            if (credentialResult.isFailure) {
                val error = credentialResult.exceptionOrNull()
                if (error is RestoreCredentialError.BackupUnavailable) {
                    // True silent-success: this device doesn't support cloud restore credentials at
                    // all (e.g. iOS without iCloud Keychain, Android without backup configured).
                    // The user is not at risk of losing their account because there was nothing
                    // to register in the first place.
                    Napier.i("Restore key skipped — device does not support E2EE backup")
                    return Result.success(Unit)
                }
                // Anything else is a real failure: device supports restore credentials but the OS
                // refused to mint one (user cancellation, biometric setup missing, transient
                // platform error). Propagate so the caller can prompt the user to retry instead
                // of leaving the account without a recovery path.
                Napier.w("Restore key registration failed: device refused credential creation", error)
                return Result.failure(error ?: Exception("Restore credential creation failed"))
            }

            val completeResult =
                withFreshAccessToken { accessToken ->
                    apiClient.completeRestoreKeyRegistration(
                        accessToken = accessToken,
                        credentialJson = credentialResult.getOrThrow(),
                        challenge = options.challenge,
                    )
                }
            if (completeResult.isFailure) {
                val cause = completeResult.exceptionOrNull()
                Napier.w("Restore key registration failed: server rejected /auth/restore/register/complete", cause)
                return Result.failure(cause ?: Exception("Restore key complete failed"))
            }
            Napier.i("Restore key registered successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.w("Restore key creation encountered an unexpected error", e)
            Result.failure(e)
        }
    }

    /**
     * Run [block] with a valid access token, transparently refreshing on first failure.
     *
     * Used everywhere we make an authenticated request. Without this, every authenticated call
     * site would have to inline the same "fail → refreshAuthentication → retry once" structure,
     * and missing it (e.g. updateAccountProfile, restore-key registration) silently surfaces a
     * generic error to users when the access token has expired in the background.
     *
     * Refreshes opportunistically on any failure, not just 401, to match the existing inline
     * pattern in [getAccountInfo] / [deletePasskey]. The cost is one extra refresh attempt when
     * a non-auth failure happens; the benefit is symmetry with the existing pattern and no need
     * to introspect server error shapes.
     */
    private suspend fun <T> withFreshAccessToken(block: suspend (accessToken: String) -> Result<T>): Result<T> {
        val session =
            sessionStorage.getSession()
                ?: return Result.failure(IllegalStateException("No active session"))

        val first = block(session.accessToken)
        if (first.isSuccess) return first

        val refreshResult = refreshAuthentication()
        if (refreshResult.isFailure) return first

        val updatedSession = sessionStorage.getSession() ?: return first
        return block(updatedSession.accessToken)
    }

    override suspend fun signInWithRestoreKey(): Result<LogDateAccount> {
        if (!currentServerSupportsCanonicalOwnerBinding()) {
            return Result.failure(UnsupportedCanonicalOwnerBindingException())
        }
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
            if (!belongsToCanonicalOwner(completeData.account)) {
                return Result.failure(CanonicalOwnerMismatchException())
            }
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
            createRestoreKey().onFailure { error ->
                Napier.w("Restore sign-in succeeded but re-registering restore key failed — next restore won't work", error)
            }

            Napier.i("Restore sign-in successful for user: ${completeData.account.username}")
            Result.success(completeData.account)
        } catch (e: Exception) {
            Napier.i("Restore sign-in failed: ${e.message ?: "unknown error"}")
            Result.failure(e)
        }
    }

    private fun requireCanonicalOwnerBinding() {
        if (!currentServerSupportsCanonicalOwnerBinding()) {
            throw UnsupportedCanonicalOwnerBindingException()
        }
    }

    private fun currentServerSupportsCanonicalOwnerBinding(): Boolean {
        if (configRepository.getCurrentBackendUrl() == app.logdate.shared.config.DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL) {
            return true
        }
        return configRepository
            .getCurrentServerDescriptor()
            ?.hasProtocolFeature(ServerProtocolFeature.CANONICAL_OWNER_BINDING_V1) == true
    }

    private class UnsupportedCanonicalOwnerBindingException :
        IllegalStateException(
            "This LogDate server does not support the required single-identity protocol",
        )

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
