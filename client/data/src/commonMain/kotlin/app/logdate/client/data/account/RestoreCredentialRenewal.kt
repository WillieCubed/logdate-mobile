package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/** Remembers a consumed restore key until the signed-in installation registers its replacement. */
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
        platform.updateTokens(account.username, origin, session.accessToken, session.refreshToken).onFailure {
            currentCoroutineContext().ensureActive()
            checkCurrent()
            platform.addAccount(account, session.accessToken, session.refreshToken, origin).getOrThrow()
        }
        currentCoroutineContext().ensureActive()
        checkCurrent()
        val options = api.beginRestoreKeyRegistration(session.accessToken).getOrThrow()
        currentCoroutineContext().ensureActive()
        checkCurrent()
        val credential = credentials.createRestoreKey(options).getOrThrow()
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
        val bound = sessions.getOriginBoundSession() ?: return
        if (bound.origin != origin || bound.session != session || !isCurrent()) return
        val api = apiFor(config) ?: return
        val token = api.refreshToken(session.refreshToken).getOrThrow()
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) return
        sessions.replaceSessionIfCurrent(bound, session.copy(accessToken = token))
    }

    private fun key(origin: String): String = "restore_key_renewal_${Base64.UrlSafe.encode(origin.encodeToByteArray())}"
}
