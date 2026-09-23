@file:Suppress("ktlint:standard:filename")

package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.GoogleSignInManager
import app.logdate.client.permissions.NoOpGoogleSignInManager
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.LinkedSignInProvider
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyInfo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Default implementation of PasskeyAccountRepository for managing passkey-based account operations.
 *
 * Delegates each family of operations to a focused collaborator: [PasskeyRegistrationCoordinator]
 * for passkey creation/sign-in, [GoogleSignInCoordinator] for Google sign-up/sign-in,
 * [AccountSessionRefreshCoordinator] for token refresh and the authenticated passkey endpoints, and
 * [RestoreCredentialCoordinator] for cloud-backup restore credentials. [CanonicalOwnerBindingGuard]
 * enforces LogDate's single-identity rule for all of them, and [PasskeyAccountSessionState] is the
 * shared current-account/authenticated state they report back to.
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
    /** The name of this device, given to passkeys it creates. `null` leaves the server default. */
    private val deviceName: () -> String? = { null },
) : PasskeyAccountRepository {
    private val sessionState = PasskeyAccountSessionState()
    override val currentAccount: StateFlow<LogDateAccount?> = sessionState.currentAccount
    override val isAuthenticated: StateFlow<Boolean> = sessionState.isAuthenticated

    private val bindingGuard =
        CanonicalOwnerBindingGuard(
            canonicalOwnerProvider = canonicalOwnerProvider,
            hasLocalData = hasLocalData,
            configRepository = configRepository,
        )

    private val credentialCodec = PasskeyCredentialCodec(json)

    private val sessionRefreshCoordinator =
        AccountSessionRefreshCoordinator(
            apiClient = apiClient,
            sessionStorage = sessionStorage,
            platformAccountManager = platformAccountManager,
            configRepository = configRepository,
            sessionState = sessionState,
            deleteRestoreKey = { deleteRestoreKey() },
        )

    private val restoreCredentialCoordinator =
        RestoreCredentialCoordinator(
            apiClient = apiClient,
            restoreCredentialManager = restoreCredentialManager,
            sessionStorage = sessionStorage,
            platformAccountManager = platformAccountManager,
            configRepository = configRepository,
            bindingGuard = bindingGuard,
            credentialCodec = credentialCodec,
            sessionState = sessionState,
            sessionRefreshCoordinator = sessionRefreshCoordinator,
        )

    private val registrationCoordinator =
        PasskeyRegistrationCoordinator(
            apiClient = apiClient,
            passkeyManager = passkeyManager,
            sessionStorage = sessionStorage,
            platformAccountManager = platformAccountManager,
            configRepository = configRepository,
            canonicalOwnerProvider = canonicalOwnerProvider,
            bindingGuard = bindingGuard,
            credentialCodec = credentialCodec,
            sessionState = sessionState,
            createRestoreKey = { createRestoreKey() },
            updateTokensOrRegisterPlatformAccount = sessionRefreshCoordinator::updateTokensOrRegisterPlatformAccount,
            deviceName = deviceName,
        )

    private val enrollmentCoordinator =
        PasskeyEnrollmentCoordinator(
            apiClient = apiClient,
            passkeyManager = passkeyManager,
            credentialCodec = credentialCodec,
            sessionRefreshCoordinator = sessionRefreshCoordinator,
            deviceName = deviceName,
        )

    private val googleSignInCoordinator =
        GoogleSignInCoordinator(
            apiClient = apiClient,
            sessionStorage = sessionStorage,
            platformAccountManager = platformAccountManager,
            configRepository = configRepository,
            canonicalOwnerProvider = canonicalOwnerProvider,
            bindingGuard = bindingGuard,
            googleSignInManager = googleSignInManager,
            serverClientId = serverClientId,
            sessionState = sessionState,
            createRestoreKey = { createRestoreKey() },
        )

    init {
        repositoryScope.launch {
            sessionStorage.getSessionFlow().collect { session ->
                if (session != null && !bindingGuard.sessionBelongsToCanonicalOwner(session)) {
                    Napier.w("Discarding credentials for a different LogDate identity")
                    sessionStorage.clearSession()
                    sessionState.clear()
                } else {
                    sessionState.setAuthenticated(session != null)
                    if (session == null) {
                        sessionState.clearAccount()
                    }
                }
            }
        }

        repositoryScope.launch {
            configRepository.backendUrl.drop(1).collect {
                sessionState.clearAccount()
                val session = sessionStorage.getSession()
                sessionState.setAuthenticated(session != null && bindingGuard.sessionBelongsToCanonicalOwner(session))
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

    override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> =
        registrationCoordinator.createAccountWithPasskey(request)

    override suspend fun authenticateWithPasskey(
        username: String?,
        adoptLocalData: Boolean,
    ): Result<LogDateAccount> = registrationCoordinator.authenticateWithPasskey(username, adoptLocalData)

    override suspend fun signUpWithGoogle(
        username: String?,
        displayName: String?,
    ): Result<LogDateAccount> = googleSignInCoordinator.signUpWithGoogle(username, displayName)

    override suspend fun signInWithGoogle(): Result<LogDateAccount> = googleSignInCoordinator.signInWithGoogle()

    override suspend fun signOut(): Result<Unit> = sessionRefreshCoordinator.signOut()

    override suspend fun refreshAuthentication(): Result<Unit> = sessionRefreshCoordinator.refreshAuthentication()

    override suspend fun getAccountInfo(): Result<LogDateAccount> = sessionRefreshCoordinator.getAccountInfo()

    override suspend fun getCurrentAccount(): LogDateAccount? = sessionState.account

    override suspend fun listPasskeys(): Result<List<PasskeyInfo>> = sessionRefreshCoordinator.listPasskeys()

    override suspend fun deletePasskey(credentialId: String): Result<Unit> = sessionRefreshCoordinator.deletePasskey(credentialId)

    override suspend fun addPasskey(): Result<PasskeyInfo> = enrollmentCoordinator.addPasskey()

    override suspend fun listLinkedSignInProviders(): Result<List<LinkedSignInProvider>> = enrollmentCoordinator.listLinkedSignInProviders()

    override suspend fun createRestoreKey(): Result<Unit> = restoreCredentialCoordinator.createRestoreKey()

    override suspend fun signInWithRestoreKey(): Result<LogDateAccount> = restoreCredentialCoordinator.signInWithRestoreKey()

    override suspend fun deleteRestoreKey(): Result<Unit> = restoreCredentialCoordinator.deleteRestoreKey()
}
