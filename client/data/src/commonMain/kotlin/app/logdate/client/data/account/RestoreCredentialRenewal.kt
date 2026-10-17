package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.addAccountIfCurrent
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.updateTokensIfCurrent
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.client.permissions.createRestoreKeyIfCurrent
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/** Persists enrollment after restore, including compatibility with servers that consumed restore keys. */
class RestoreCredentialRenewal(
    private val storage: SecureStorage,
    private val apiFactory: ((LogDateConfigRepository) -> PasskeyApiClientContract)? = null,
) {
    private val markerMutex = Mutex()

    internal fun apiFor(config: LogDateConfigRepository): PasskeyApiClientContract? {
        val origin = config.getCurrentBackendUrl().trimEnd('/')
        require(config.getCurrentApiBaseUrl().startsWith("$origin/"))
        require(
            config
                .getCurrentServerDescriptor()
                ?.serverOrigin
                ?.trimEnd('/')
                ?.let { it == origin } != false,
        )
        return apiFactory?.invoke(config)
    }

    suspend fun mark(
        origin: String,
        owner: String,
    ) = markerMutex.withLock { storage.putString(key(origin), "$owner\n${Uuid.random()}") }

    internal suspend fun ensurePendingIfAbsent(
        origin: String,
        owner: String,
    ) = markerMutex.withLock {
        if (storage.getString(key(origin)) == null) storage.putString(key(origin), "$owner\n${Uuid.random()}")
    }

    suspend fun isPending(
        origin: String,
        owner: String,
    ): Boolean = storage.getString(key(origin))?.substringBefore('\n') == owner

    private suspend fun complete(
        origin: String,
        expected: String,
    ) = markerMutex.withLock {
        if (storage.getString(key(origin)) == expected) storage.putString(key(origin), "resolved")
    }

    internal suspend fun renew(
        config: LogDateConfigRepository,
        session: UserSession,
        credentials: RestoreCredentialManager,
        platform: PlatformAccountManager,
        isCurrent: () -> Boolean,
    ) {
        val origin = config.getCurrentBackendUrl()
        val obligation = storage.getString(key(origin)) ?: return
        if (obligation.substringBefore('\n') != session.accountId) return
        val api = apiFor(config) ?: return

        fun checkCurrent() {
            check(isCurrent()) { "Restore key renewal account changed" }
        }
        checkCurrent()
        val account = api.getAccountInfo(session.accessToken).getOrThrow()
        currentCoroutineContext().ensureActive()
        checkCurrent()
        check(account.id.toString() == session.accountId)
        platform.updateTokensIfCurrent(account.username, origin, session.accessToken, session.refreshToken) { isCurrent() }.onFailure {
            currentCoroutineContext().ensureActive()
            checkCurrent()
            platform.addAccountIfCurrent(account, session.accessToken, session.refreshToken, origin) { isCurrent() }.getOrThrow()
        }
        currentCoroutineContext().ensureActive()
        checkCurrent()
        val options = api.beginRestoreKeyRegistration(session.accessToken).getOrThrow()
        currentCoroutineContext().ensureActive()
        checkCurrent()
        val credential = credentials.createRestoreKeyIfCurrent(options, isCurrent).getOrThrow()
        currentCoroutineContext().ensureActive()
        checkCurrent()
        api.completeRestoreKeyRegistration(session.accessToken, credential, options.challenge).getOrThrow()
        currentCoroutineContext().ensureActive()
        checkCurrent()
        complete(origin, obligation)
    }

    /** Refresh only the captured origin/session; a concurrent switch cannot receive its tokens. */
    internal suspend fun refreshSession(
        config: LogDateConfigRepository,
        session: UserSession,
        sessions: SessionStorage,
        isCurrent: () -> Boolean,
    ) {
        val origin = config.getCurrentBackendUrl()
        val captured = sessions.captureAuthenticationSnapshot()
        if (captured == null && sessions.supportsGuardedAuthentication) return
        val bound = (if (captured != null) captured.session else sessions.getOriginBoundSession()) ?: return
        if (bound.origin != origin || bound.session != session || !isCurrent()) return
        val api = apiFor(config) ?: return
        val token = api.refreshToken(session.refreshToken).getOrThrow()
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) return
        sessions.replaceSessionIfAuthorized(bound, session.copy(accessToken = token), captured?.guard)
    }

    private fun key(origin: String): String = "restore_key_renewal_${Base64.UrlSafe.encode(origin.encodeToByteArray())}"
}
