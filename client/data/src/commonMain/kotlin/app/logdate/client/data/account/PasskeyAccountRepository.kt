@file:Suppress("ktlint:standard:filename")

package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * Whether signing up creates the app's restore credential. There is one per app, so an account
     * opened on a server the app isn't connected to must not replace the connected server's.
     */
    private val createsRestoreKey: Boolean = true,
    private val adoptCanonicalOwner: (suspend (String) -> Boolean)? = null,
    private val accountKeyUnlockCoordinator: AccountKeyUnlockCoordinator? = null,
    private val beforeSignOut: suspend () -> Boolean = { true },
    private val restoreCredentialRenewal: RestoreCredentialRenewal? = null,
) : PasskeyAccountRepository {
    private val sessionState = PasskeyAccountSessionState()
    override val currentAccount: StateFlow<LogDateAccount?> = sessionState.currentAccount
    override val isAuthenticated: StateFlow<Boolean> = sessionState.isAuthenticated

    private val bindingGuard =
        CanonicalOwnerBindingGuard(
            canonicalOwnerProvider = canonicalOwnerProvider,
            hasLocalData = hasLocalData,
            configRepository = configRepository,
            adoptCanonicalOwner = adoptCanonicalOwner,
        )

    private val credentialCodec = PasskeyCredentialCodec(json)
    private val restoreCredentialRegistrationMutex = Mutex()
    private val restoreRenewalRequests = MutableStateFlow(0L)

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
            configRepository = configRepository,
            bindingGuard = bindingGuard,
            sessionState = sessionState,
            sessionRefreshCoordinator = sessionRefreshCoordinator,
            registrationMutex = restoreCredentialRegistrationMutex,
            renewal = restoreCredentialRenewal,
            onSessionInstalled = { restoreRenewalRequests.update { it + 1 } },
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
            createRestoreKey = { if (createsRestoreKey) createRestoreKey() else Result.success(Unit) },
            updateTokensOrRegisterPlatformAccount = sessionRefreshCoordinator::updateTokensOrRegisterPlatformAccount,
            deviceName = deviceName,
            accountKeyUnlockCoordinator = accountKeyUnlockCoordinator,
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
            createRestoreKey = { if (createsRestoreKey) createRestoreKey() else Result.success(Unit) },
        )

    init {
        repositoryScope.launch {
            combine(sessionStorage.getSessionFlow(), restoreRenewalRequests) { session, _ -> session }.collectLatest { session ->
                if (session != null && !bindingGuard.sessionBelongsToCanonicalOwner(session)) {
                    Napier.w("Discarding credentials for a different LogDate identity")
                    sessionStorage.clearSession()
                    sessionState.clear()
                } else {
                    sessionState.setAuthenticated(session != null)
                    if (session == null) {
                        sessionState.clearAccount()
                    } else {
                        renewBackgroundRestoreKey(session)
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

    private suspend fun renewBackgroundRestoreKey(session: app.logdate.client.datastore.UserSession) {
        val renewal = restoreCredentialRenewal ?: return
        val origin = configRepository.getCurrentBackendUrl()
        val pinned =
            app.logdate.shared.config.PinnedLogDateConfigRepository(
                origin,
                configRepository.getCurrentServerDescriptor(),
                configRepository.apiVersion.value,
            )
        val isCurrent = { configRepository.getCurrentBackendUrl() == origin && sessionStorage.getSession() == session }
        retryRestoreCredentialRenewal(
            isPending = { renewal.isPending(origin, session.accountId) },
            isCurrent = isCurrent,
            renew = {
                restoreCredentialRegistrationMutex.withLock {
                    renewal.renew(pinned, session, restoreCredentialManager, platformAccountManager, isCurrent)
                }
            },
            onFailure = { error ->
                if (error is PasskeyApiException &&
                    error.errorCode in setOf(PasskeyApiErrorCodes.INVALID_TOKEN, PasskeyApiErrorCodes.UNAUTHORIZED)
                ) {
                    renewal.refreshSession(pinned, session, sessionStorage, isCurrent)
                }
            },
        )
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

    override suspend fun signOut(): Result<Unit> =
        if (beforeSignOut()) {
            sessionRefreshCoordinator.signOut()
        } else {
            Result.failure(IllegalStateException("The linked watch could not be removed safely. Keep it nearby and try again."))
        }

    override suspend fun deleteAccount(): Result<Unit> = sessionRefreshCoordinator.deleteAccount()

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
