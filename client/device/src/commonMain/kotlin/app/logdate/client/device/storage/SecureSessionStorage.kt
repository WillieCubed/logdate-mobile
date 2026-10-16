package app.logdate.client.device.storage

import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.SessionInstallationGuard
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.LogDateConfigRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
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
import kotlin.uuid.Uuid

/**
 * SessionStorage backed by SecureStorage for token persistence.
 */
class SecureSessionStorage(
    private val secureStorage: SecureStorage,
    private val configRepository: LogDateConfigRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val privacyEpoch: app.logdate.shared.config.PrivacyScopeEpoch? = null,
    private val nativeRestorePolicy: app.logdate.client.datastore.NativeRestorePolicy? = null,
    private val connectedVault: SecureSessionStorage? = null,
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

    private data class GuardSnapshot(
        val record: String?,
        val decision: String?,
    )

    private val guards = linkedMapOf<String, GuardSnapshot>()
    override val supportsGuardedAuthentication = true

    private companion object {
        // Native backup callbacks construct a separate vault over the same secure store.
        val identityMutex = kotlinx.coroutines.sync.Mutex()
    }

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

    override suspend fun captureInstallationGuard(): SessionInstallationGuard? = captureGuard(false)

    override suspend fun captureAuthenticationGuard(): SessionInstallationGuard? = captureGuard(true)

    private suspend fun captureGuard(explicit: Boolean): SessionInstallationGuard? {
        val origin = configRepository.getCurrentBackendUrl()
        if (sessionState.value.origin != origin) loadCurrentSession(origin)
        return identityMutex.withLock {
            val decision = nativeRestorePolicy?.decision(origin)
            val record = secureStorage.getString(scopedKey(StorageKeys.RECORD, origin))
            if (!explicit && (!NativeRestorePolicy.allows(decision) || record == "null")) return@withLock null
            val cached = sessionState.value
            if (cached.origin != origin || configRepository.getCurrentBackendUrl() != origin) return@withLock null
            val expected = cached.session?.let { encodeSession(it) }
            val hiddenSignedOutRecord = cached.session == null && (record == "null" || !NativeRestorePolicy.allows(decision))
            if (record != expected && !hiddenSignedOutRecord) {
                sessionState.compareAndSet(cached, CachedSession(null, null, cached.revision + 1))
                return@withLock null
            }
            val token = Uuid.random().toString()
            if (guards.size >= 64) guards.remove(guards.keys.first())
            guards[token] = GuardSnapshot(record, decision)
            SessionInstallationGuard(origin, cached.revision, token)
        }
    }

    override suspend fun installSessionIfCurrent(
        expected: SessionInstallationGuard,
        session: UserSession,
    ): Boolean = install(expected, session, false)

    override suspend fun installExplicitSessionIfCurrent(
        expected: SessionInstallationGuard,
        session: UserSession,
    ): Boolean = install(expected, session, true)

    private suspend fun install(
        expected: SessionInstallationGuard,
        session: UserSession,
        explicit: Boolean,
    ): Boolean =
        mutateIdentity({ getSession()?.accountId != session.accountId }) {
            val snapshot = guards.remove(expected.token) ?: return@mutateIdentity false
            val cached = sessionState.value
            if (cached.origin != expected.origin ||
                cached.revision != expected.revision ||
                configRepository.getCurrentBackendUrl() != expected.origin
            ) {
                return@mutateIdentity false
            }
            val decision = nativeRestorePolicy?.decision(expected.origin)
            val persisted = secureStorage.getString(scopedKey(StorageKeys.RECORD, expected.origin))
            if (persisted != snapshot.record || decision != snapshot.decision || (!explicit && !NativeRestorePolicy.allows(decision))) {
                sessionState.compareAndSet(cached, CachedSession(null, null, cached.revision + 1))
                return@mutateIdentity false
            }
            try {
                persistSession(expected.origin, session)
                if (explicit) nativeRestorePolicy?.setAllowed(expected.origin, true)
            } catch (error: Exception) {
                sessionState.compareAndSet(cached, CachedSession(null, null, cached.revision + 1))
                invalidateConnectedCache(expected.origin)
                throw error
            }
            if (configRepository.getCurrentBackendUrl() != expected.origin) return@mutateIdentity false
            val installed = sessionState.compareAndSet(cached, CachedSession(expected.origin, session, cached.revision + 1))
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            installed
        }

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
            if (nativeRestorePolicy?.canRestore(expected.origin) == false ||
                secureStorage.getString(scopedKey(StorageKeys.RECORD, expected.origin)) != encodeSession(expected.session)
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
        if (expected.origin == origin) {
            return expected.session != null && configRepository.getCurrentBackendUrl() == origin
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
        writeSession(origin, session, false)
    }

    override suspend fun saveExplicitSession(session: UserSession) = writeSession(configRepository.getCurrentBackendUrl(), session, true)

    private suspend fun persistSession(
        origin: String,
        session: UserSession,
    ) {
        secureStorage.putString(
            scopedKey(StorageKeys.RECORD, origin),
            encodeSession(session),
        )
    }

    private fun encodeSession(session: UserSession): String =
        Json.encodeToString(StoredSession(session.accessToken, session.refreshToken, session.accountId))

    override suspend fun clearSession() = clear(configRepository.getCurrentBackendUrl())

    override suspend fun read(origin: String): UserSession? = loadSession(origin)

    override suspend fun openSessionStorage(
        origin: String,
        scope: CoroutineScope,
    ): SessionStorage =
        SecureSessionStorage(
            secureStorage,
            app.logdate.shared.config
                .PinnedLogDateConfigRepository(origin, null),
            scope,
            nativeRestorePolicy = nativeRestorePolicy,
            connectedVault = connectedVault ?: this,
        ).also { it.hasValidSession() }

    override suspend fun write(
        origin: String,
        session: UserSession,
    ) = writeSession(origin, session, true)

    private suspend fun writeSession(
        origin: String,
        session: UserSession,
        explicit: Boolean,
    ) {
        mutateIdentity({ origin == configRepository.getCurrentBackendUrl() && getSession()?.accountId != session.accountId }) {
            try {
                check(explicit || nativeRestorePolicy?.canRestore(origin) != false) { "Session authorization changed" }
                persistSession(origin, session)
                if (explicit) nativeRestorePolicy?.setAllowed(origin, true)
            } catch (error: Exception) {
                if (origin == configRepository.getCurrentBackendUrl()) sessionState.update { CachedSession(null, null, it.revision + 1) }
                invalidateConnectedCache(origin)
                throw error
            }
            if (origin == configRepository.getCurrentBackendUrl()) cache(origin, session)
        }
    }

    override suspend fun clear(origin: String) {
        mutateIdentity({ origin == configRepository.getCurrentBackendUrl() }) { clearLocked(origin) }
    }

    override suspend fun clearSessionIfCurrent(expected: OriginBoundSession): Boolean =
        mutateIdentity({ true }) {
            if (getOriginBoundSession() != expected ||
                secureStorage.getString(scopedKey(StorageKeys.RECORD, expected.origin)) != encodeSession(expected.session)
            ) {
                return@mutateIdentity false
            }
            clearLocked(expected.origin)
            true
        }

    private suspend fun clearLocked(origin: String) {
        try {
            nativeRestorePolicy?.setAllowed(origin, false)
            // The secure tombstone also prevents legacy-token fallback after interrupted cleanup.
            secureStorage.putString(scopedKey(StorageKeys.RECORD, origin), "null")
        } finally {
            if (origin == configRepository.getCurrentBackendUrl()) cache(origin, null)
        }
    }

    private fun invalidateConnectedCache(origin: String) {
        val host = connectedVault ?: return
        if (host.configRepository.getCurrentBackendUrl() == origin) {
            host.sessionState.update { CachedSession(null, null, it.revision + 1) }
        }
    }

    private suspend fun <T> mutateIdentity(
        changed: () -> Boolean,
        publish: suspend () -> T,
    ): T =
        identityMutex.withLock {
            val host = connectedVault ?: this
            val origin = configRepository.getCurrentBackendUrl()
            val before = sessionState.value
            val transition =
                suspend {
                    try {
                        publish()
                    } finally {
                        val after = sessionState.value
                        if (host !== this && after != before && host.configRepository.getCurrentBackendUrl() == origin) {
                            if (after.origin == origin) {
                                host.cache(origin, after.session)
                            }
                        }
                    }
                }
            val epoch = host.privacyEpoch
            if (epoch == null) {
                transition()
            } else {
                epoch.transition(
                    {
                        host.configRepository.getCurrentBackendUrl() == origin &&
                            (changed() || host.getSession()?.accountId != getSession()?.accountId)
                    },
                    transition,
                )
            }
        }

    private suspend fun loadSession(backendUrl: String): UserSession? =
        identityMutex.withLock {
            try {
                if (nativeRestorePolicy?.canRestore(backendUrl) == false) return@withLock null
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
