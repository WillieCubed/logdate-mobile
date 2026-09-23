package app.logdate.client.data.account

import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.PasskeyApiClient
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.config.PinnedLogDateConfigRepository
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.ServerDescriptor
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * An account on one particular server, reached without changing which server the app is
 * connected to.
 */
interface ServerScopedAccount {
    val origin: String

    /** Signs in, creates the account, or deletes it -- always against [origin]. */
    val repository: PasskeyAccountRepository

    /** The sign-in for [origin] this account holds, if any. */
    fun session(): UserSession?

    /** Stops the account's background work. */
    fun close()
}

/** Opens accounts on servers other than the connected one. */
fun interface ServerScopedAccounts {
    /**
     * Opens the account on [origin], starting from any sign-in already saved for it.
     * [descriptor] must be the server's own description of itself.
     */
    suspend fun open(
        origin: String,
        descriptor: ServerDescriptor,
    ): ServerScopedAccount
}

/**
 * Builds a full passkey account repository whose configuration, network client and saved sign-in
 * all belong to one server. It shares the platform passkey prompt, platform accounts and this
 * device's owner ID with the app, but not the app's restore credential: there is one per app, and
 * a scoped account creating one would replace the connected server's.
 */
class DefaultServerScopedAccounts(
    private val httpClient: HttpClient,
    private val vault: OriginSessionVault,
    private val passkeyManager: PasskeyManager,
    private val platformAccountManager: PlatformAccountManager,
    private val canonicalOwnerProvider: CanonicalOwnerProvider,
    private val hasLocalData: suspend () -> Boolean,
    private val deviceName: () -> String?,
) : ServerScopedAccounts {
    override suspend fun open(
        origin: String,
        descriptor: ServerDescriptor,
    ): ServerScopedAccount {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val config = PinnedLogDateConfigRepository(origin, descriptor)
        val sessionStorage =
            OriginPinnedSessionStorage(vault, config.getCurrentBackendUrl(), vault.read(config.getCurrentBackendUrl()), scope)
        val repository =
            DefaultPasskeyAccountRepository(
                apiClient = PasskeyApiClient(httpClient, config),
                passkeyManager = passkeyManager,
                restoreCredentialManager = NoRestoreCredentialManager,
                sessionStorage = sessionStorage,
                platformAccountManager = platformAccountManager,
                configRepository = config,
                canonicalOwnerProvider = canonicalOwnerProvider,
                hasLocalData = hasLocalData,
                repositoryScope = scope,
                deviceName = deviceName,
                createsRestoreKey = false,
            )
        return object : ServerScopedAccount {
            override val origin: String = config.getCurrentBackendUrl()
            override val repository: PasskeyAccountRepository = repository

            override fun session(): UserSession? = sessionStorage.getSession()

            override fun close() = scope.cancel()
        }
    }
}

/**
 * A [SessionStorage] that always reads and writes the sign-in saved for one server.
 */
internal class OriginPinnedSessionStorage(
    private val vault: OriginSessionVault,
    private val origin: String,
    initial: UserSession?,
    private val scope: CoroutineScope,
) : SessionStorage {
    private val state = MutableStateFlow(initial)

    override fun getSession(): UserSession? = state.value

    override fun getSessionFlow(): StateFlow<UserSession?> = state.asStateFlow()

    override suspend fun hasValidSession(): Boolean = state.value != null

    override fun saveSession(session: UserSession) {
        state.value = session
        scope.launch {
            runCatching { vault.write(origin, session) }.onFailure { Napier.e("Failed to save the sign-in for $origin", it) }
        }
    }

    override fun clearSession() {
        state.value = null
        scope.launch {
            runCatching { vault.clear(origin) }.onFailure { Napier.e("Failed to clear the sign-in for $origin", it) }
        }
    }
}

/** Declines to create or use restore credentials; see [DefaultServerScopedAccounts]. */
private object NoRestoreCredentialManager : RestoreCredentialManager {
    override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> =
        Result.failure(UnsupportedOperationException("Restore credentials belong to the connected server"))

    override suspend fun getRestoreCredential(options: PasskeyAuthenticationOptions): Result<String> =
        Result.failure(UnsupportedOperationException("Restore credentials belong to the connected server"))

    override suspend fun clearRestoreCredential(): Result<Unit> = Result.success(Unit)
}
