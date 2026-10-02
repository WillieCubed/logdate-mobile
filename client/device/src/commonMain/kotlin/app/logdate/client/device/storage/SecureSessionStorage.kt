package app.logdate.client.device.storage

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.LogDateConfigRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.text.encodeToByteArray

/**
 * SessionStorage backed by SecureStorage for token persistence.
 */
class SecureSessionStorage(
    private val secureStorage: SecureStorage,
    private val configRepository: LogDateConfigRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val privacyEpoch: app.logdate.shared.config.PrivacyScopeEpoch? = null,
) : SessionStorage,
    OriginSessionVault {
    @Serializable
    private data class StoredSession(
        val accessToken: String,
        val refreshToken: String,
        val accountId: String,
    )

    private object StorageKeys {
        const val RECORD = "session_record_v2"
        const val ACCESS_TOKEN = "session_access_token"
        const val REFRESH_TOKEN = "session_refresh_token"
        const val ACCOUNT_ID = "session_account_id"
    }

    private data class CachedSession(
        val origin: String?,
        val session: UserSession?,
        val revision: Long,
    )

    private val sessionState = MutableStateFlow(CachedSession(null, null, 0))
    private val identityMutex = kotlinx.coroutines.sync.Mutex()

    init {
        scope.launch {
            configRepository.backendUrl.collect { origin -> loadCurrentSession(origin) }
        }
    }

    override fun getOriginBoundSession(): OriginBoundSession? {
        val cached = sessionState.value
        if (cached.origin != configRepository.getCurrentBackendUrl()) return null
        return cached.session?.let { OriginBoundSession(requireNotNull(cached.origin), it) }
    }

    override fun getSession(): UserSession? = getOriginBoundSession()?.session

    override suspend fun replaceSessionIfCurrent(
        expected: OriginBoundSession,
        updated: UserSession,
    ): Boolean =
        mutateIdentity({ false }) {
            val cached = sessionState.value
            if (cached.origin != expected.origin ||
                cached.session != expected.session ||
                configRepository.getCurrentBackendUrl() != expected.origin ||
                updated.accountId != expected.session.accountId
            ) {
                return@mutateIdentity false
            }
            persistSession(expected.origin, updated)
            sessionState.compareAndSet(cached, CachedSession(expected.origin, updated, cached.revision + 1))
        }

    override fun getSessionFlow() = combine(sessionState, configRepository.backendUrl) { _, _ -> getSession() }.distinctUntilChanged()

    override suspend fun hasValidSession(): Boolean {
        if (getSession() != null) return true
        return loadCurrentSession(configRepository.getCurrentBackendUrl())
    }

    private suspend fun loadCurrentSession(origin: String): Boolean {
        val expected = sessionState.value
        if (expected.origin == origin && expected.session != null) {
            return configRepository.getCurrentBackendUrl() == origin
        }
        val loaded = loadSession(origin)
        if (configRepository.getCurrentBackendUrl() != origin) return false
        sessionState.compareAndSet(expected, CachedSession(origin, loaded, expected.revision + 1))
        return getSession() != null
    }

    private fun cache(
        origin: String,
        session: UserSession?,
    ) {
        sessionState.update { CachedSession(origin, session, it.revision + 1) }
    }

    override suspend fun saveSession(session: UserSession) {
        val origin = configRepository.getCurrentBackendUrl()
        write(origin, session)
    }

    private suspend fun persistSession(
        origin: String,
        session: UserSession,
    ) {
        secureStorage.putString(
            scopedKey(StorageKeys.RECORD, origin),
            Json.encodeToString(StoredSession(session.accessToken, session.refreshToken, session.accountId)),
        )
    }

    override suspend fun clearSession() = clear(configRepository.getCurrentBackendUrl())

    override suspend fun read(origin: String): UserSession? = loadSession(origin)

    override suspend fun write(
        origin: String,
        session: UserSession,
    ) {
        mutateIdentity({
            origin == configRepository.getCurrentBackendUrl() && getSession()?.accountId != session.accountId
        }) {
            persistSession(origin, session)
            if (origin == configRepository.getCurrentBackendUrl()) cache(origin, session)
        }
    }

    override suspend fun clear(origin: String) {
        mutateIdentity({ origin == configRepository.getCurrentBackendUrl() }) {
            // A tombstone prevents fallback to legacy credentials if their cleanup is interrupted.
            secureStorage.putString(scopedKey(StorageKeys.RECORD, origin), "null")
            if (origin == configRepository.getCurrentBackendUrl()) cache(origin, null)
        }
    }

    private suspend fun <T> mutateIdentity(
        changed: () -> Boolean,
        publish: suspend () -> T,
    ): T =
        identityMutex.withLock {
            val epoch = privacyEpoch
            if (epoch == null) publish() else epoch.transition(changed, publish)
        }

    private suspend fun loadSession(backendUrl: String): UserSession? =
        identityMutex.withLock {
            try {
                val record = secureStorage.getString(scopedKey(StorageKeys.RECORD, backendUrl))
                if (record != null) {
                    if (record == "null") return@withLock null
                    val stored = Json.decodeFromString<StoredSession>(record)
                    return@withLock UserSession(stored.accessToken, stored.refreshToken, stored.accountId)
                }
                migrateLegacyKeysIfNeeded(backendUrl)
                val accessToken = secureStorage.getString(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl))
                val refreshToken = secureStorage.getString(scopedKey(StorageKeys.REFRESH_TOKEN, backendUrl))
                val accountId = secureStorage.getString(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl))
                if (accessToken == null || refreshToken == null || accountId == null) return@withLock null
                UserSession(accessToken, refreshToken, accountId).also { persistSession(backendUrl, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Napier.e("SESSION_STORAGE_READ_FAILED")
                null
            }
        }

    private suspend fun migrateLegacyKeysIfNeeded(backendUrl: String) {
        if (secureStorage.getString(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl)) != null) {
            return
        }

        val legacyAccessToken = secureStorage.getString(StorageKeys.ACCESS_TOKEN) ?: return
        val legacyRefreshToken = secureStorage.getString(StorageKeys.REFRESH_TOKEN) ?: return
        val legacyAccountId = secureStorage.getString(StorageKeys.ACCOUNT_ID) ?: return

        secureStorage.putString(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl), legacyAccessToken)
        secureStorage.putString(scopedKey(StorageKeys.REFRESH_TOKEN, backendUrl), legacyRefreshToken)
        secureStorage.putString(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl), legacyAccountId)
        secureStorage.remove(StorageKeys.ACCESS_TOKEN)
        secureStorage.remove(StorageKeys.REFRESH_TOKEN)
        secureStorage.remove(StorageKeys.ACCOUNT_ID)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun scopedKey(
        baseKey: String,
        backendUrl: String,
    ): String = "${baseKey}_${Base64.UrlSafe.encode(backendUrl.trim().encodeToByteArray()).trimEnd('=')}"
}
