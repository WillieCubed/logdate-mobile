package app.logdate.client.device.storage

import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.SessionAuthenticationSnapshot
import app.logdate.client.datastore.SessionAuthorizationToken
import app.logdate.client.datastore.SessionInstallationGuard
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.LogDateConfigRepository
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
import kotlin.uuid.Uuid

/** SessionStorage backed by SecureStorage for token persistence. */
class SecureSessionStorage(
    private val secureStorage: SecureStorage,
    private val configRepository: LogDateConfigRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val privacyEpoch: app.logdate.shared.config.PrivacyScopeEpoch? = null,
    private val nativeRestorePolicy: app.logdate.client.datastore.NativeRestorePolicy? = null,
    private val connectedVault: SecureSessionStorage? = null,
) : SessionStorage,
    OriginSessionVault {
    private data class CachedSession(
        val origin: String?,
        val session: UserSession?,
        val revision: Long,
        val restoreDecision: String? = null,
    )

    private val sessionState = MutableStateFlow(CachedSession(null, null, 0))
    private val sessionLoadMutex = kotlinx.coroutines.sync.Mutex()
    private val authorizationRevisions: MutableStateFlow<Map<String, Long>> =
        connectedVault?.authorizationRevisions ?: MutableStateFlow(emptyMap())

    private class GuardSnapshot(
        val origin: String,
        val issuer: SecureSessionStorage,
        val record: String?,
        val decision: String?,
        val authorizationRevision: Long,
    ) : SessionAuthorizationToken

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

    override suspend fun captureAuthenticationSnapshot(): SessionAuthenticationSnapshot? = captureSnapshot(true)

    override suspend fun isAuthenticationGuardCurrent(expected: SessionInstallationGuard): Boolean =
        identityMutex.withLock { authenticationGuardCurrent(expected) }

    private suspend fun authenticationGuardCurrent(expected: SessionInstallationGuard): Boolean {
        val snapshot = expected.authorization as? GuardSnapshot ?: guards[expected.token] ?: return false
        if (snapshot.issuer !== this || snapshot.origin != expected.origin) return false
        val cached = sessionState.value
        if (cached.origin != expected.origin || configRepository.getCurrentBackendUrl() != expected.origin) return false
        if (nativeRestorePolicy != null) return nativeRestorePolicy.decision(expected.origin) == snapshot.decision
        val record = secureStorage.getString(scopedSessionKey(SessionRecordKeys.RECORD, expected.origin))
        return authorizationRevision(expected.origin) == snapshot.authorizationRevision &&
            (record == cached.session?.let { encodeStoredSession(it) } || (cached.session == null && record == "null"))
    }

    private suspend fun captureGuard(explicit: Boolean): SessionInstallationGuard? = captureSnapshot(explicit)?.guard

    private suspend fun captureSnapshot(explicit: Boolean): SessionAuthenticationSnapshot? {
        val origin = configRepository.getCurrentBackendUrl()
        val current = sessionState.value
        if (current.origin != origin || current.session == null || current.restoreDecision != nativeRestorePolicy?.decision(origin)) {
            loadCurrentSession(origin, refreshMissing = current.origin == origin && current.session == null)
        }
        return identityMutex.withLock {
            val decision = nativeRestorePolicy?.decision(origin)
            val record = secureStorage.getString(scopedSessionKey(SessionRecordKeys.RECORD, origin))
            if (!explicit && (!NativeRestorePolicy.allows(decision) || record == "null")) return@withLock null
            val cached = sessionState.value
            if (cached.origin != origin || configRepository.getCurrentBackendUrl() != origin) return@withLock null
            val expected = cached.session?.let { encodeStoredSession(it) }
            val hiddenSignedOutRecord = cached.session == null && (record == "null" || !NativeRestorePolicy.allows(decision))
            if (record != expected && !hiddenSignedOutRecord) {
                sessionState.compareAndSet(cached, CachedSession(null, null, cached.revision + 1))
                return@withLock null
            }
            val token = Uuid.random().toString()
            if (guards.size >= 64) guards.remove(guards.keys.first())
            val snapshot = GuardSnapshot(origin, this, record, decision, authorizationRevision(origin))
            guards[token] = snapshot
            SessionAuthenticationSnapshot(
                cached.session?.let { OriginBoundSession(origin, it) },
                SessionInstallationGuard(origin, cached.revision, token, snapshot),
            )
        }
    }

    override suspend fun installSessionIfCurrent(
        expected: SessionInstallationGuard,
        session: UserSession,
    ): Boolean = install(expected, session, false)

    suspend fun installNativeRestoreSessionIfCurrent(
        expected: SessionInstallationGuard,
        session: UserSession,
        commitEpoch: app.logdate.shared.config.PrivacyScopeEpoch? = null,
        expectedEpoch: String? = null,
        beforePersist: suspend () -> Boolean,
    ): Boolean =
        identityMutex.withLock {
            val publish = suspend { installLocked(expected, session, false, beforePersist) }
            val installed = if (commitEpoch == null) publish() else commitEpoch.withStableValue(expectedEpoch, publish) ?: false
            if (installed) {
                val host = connectedVault
                val after = sessionState.value
                if (host != null && host.configRepository.getCurrentBackendUrl() == expected.origin) {
                    host.cache(expected.origin, after.session, after.restoreDecision)
                }
            }
            installed
        }

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
            installLocked(expected, session, explicit)
        }

    private suspend fun installLocked(
        expected: SessionInstallationGuard,
        session: UserSession,
        explicit: Boolean,
        beforePersist: suspend () -> Boolean = { true },
    ): Boolean {
        val snapshot = guards.remove(expected.token) ?: return false
        val cached = sessionState.value
        if (cached.origin != expected.origin ||
            cached.revision != expected.revision ||
            configRepository.getCurrentBackendUrl() != expected.origin
        ) {
            return false
        }
        val decision = nativeRestorePolicy?.decision(expected.origin)
        val persisted = secureStorage.getString(scopedSessionKey(SessionRecordKeys.RECORD, expected.origin))
        if (persisted != snapshot.record ||
            decision != snapshot.decision ||
            authorizationRevision(expected.origin) != snapshot.authorizationRevision ||
            (!explicit && !NativeRestorePolicy.allows(decision))
        ) {
            sessionState.compareAndSet(cached, CachedSession(null, null, cached.revision + 1))
            return false
        }
        if (!beforePersist()) return false
        try {
            persistSession(expected.origin, session)
            if (explicit) {
                nativeRestorePolicy?.setAllowed(expected.origin, true)
                advanceAuthorization(expected.origin)
            }
        } catch (error: Exception) {
            sessionState.compareAndSet(cached, CachedSession(null, null, cached.revision + 1))
            invalidateConnectedCache(expected.origin)
            throw error
        }
        if (configRepository.getCurrentBackendUrl() != expected.origin) return false
        val installed =
            sessionState.compareAndSet(
                cached,
                CachedSession(expected.origin, session, cached.revision + 1, nativeRestorePolicy?.decision(expected.origin)),
            )
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return installed
    }

    override suspend fun clearSessionIfUnchanged(
        expected: SessionInstallationGuard,
        onCleared: () -> Unit,
    ): Boolean =
        mutateIdentity({ true }) {
            val snapshot = guards.remove(expected.token) ?: return@mutateIdentity false
            val cached = sessionState.value
            if (cached.origin != expected.origin ||
                cached.revision != expected.revision ||
                configRepository.getCurrentBackendUrl() != expected.origin ||
                secureStorage.getString(scopedSessionKey(SessionRecordKeys.RECORD, expected.origin)) != snapshot.record ||
                nativeRestorePolicy?.decision(expected.origin) != snapshot.decision ||
                authorizationRevision(expected.origin) != snapshot.authorizationRevision
            ) {
                return@mutateIdentity false
            }
            clearLocked(expected.origin)
            onCleared()
            true
        }

    override suspend fun replaceSessionIfCurrent(
        expected: OriginBoundSession,
        updated: UserSession,
    ): Boolean = replaceSessionIfAuthorized(expected, updated, null)

    override suspend fun replaceSessionIfAuthorized(
        expected: OriginBoundSession,
        updated: UserSession,
        authorization: SessionInstallationGuard?,
    ): Boolean =
        mutateIdentity({ false }) {
            if (authorization != null && !authenticationGuardCurrent(authorization)) return@mutateIdentity false
            val cached = sessionState.value
            if (cached.origin != expected.origin ||
                cached.session != expected.session ||
                configRepository.getCurrentBackendUrl() != expected.origin ||
                updated.accountId != expected.session.accountId
            ) {
                return@mutateIdentity false
            }
            if (nativeRestorePolicy?.canRestore(expected.origin) == false ||
                secureStorage.getString(scopedSessionKey(SessionRecordKeys.RECORD, expected.origin)) !=
                encodeStoredSession(expected.session)
            ) {
                return@mutateIdentity false
            }
            persistSession(expected.origin, updated)
            sessionState.compareAndSet(cached, CachedSession(expected.origin, updated, cached.revision + 1, cached.restoreDecision))
        }

    override fun getSessionFlow() = combine(sessionState, configRepository.backendUrl) { _, _ -> getSession() }.distinctUntilChanged()

    override suspend fun runIfSessionCurrent(
        expected: OriginBoundSession?,
        action: () -> Unit,
    ): Boolean =
        identityMutex.withLock {
            val current = sessionState.value
            if (expected == null) {
                if (current.origin != configRepository.getCurrentBackendUrl() || current.session != null) return@withLock false
                action()
                return@withLock true
            }
            if (current.origin != expected.origin ||
                configRepository.getCurrentBackendUrl() != expected.origin ||
                current.session?.accountId != expected.session.accountId ||
                current.session.refreshToken != expected.session.refreshToken ||
                nativeRestorePolicy?.canRestore(expected.origin) == false ||
                secureStorage.getString(scopedSessionKey(SessionRecordKeys.RECORD, expected.origin)) != encodeStoredSession(current.session)
            ) {
                return@withLock false
            }
            action()
            true
        }

    override suspend fun isSignedOutForProviderCleanup(): Boolean =
        identityMutex.withLock {
            val origin = configRepository.getCurrentBackendUrl()
            val disabled = nativeRestorePolicy?.canRestore(origin) == false
            val record = secureStorage.getString(scopedSessionKey(SessionRecordKeys.RECORD, origin))
            val absent =
                disabled ||
                    record == "null" ||
                    (
                        record == null &&
                            secureStorage.getString(scopedSessionKey(SessionRecordKeys.ACCESS_TOKEN, origin)) == null &&
                            secureStorage.getString(SessionRecordKeys.ACCESS_TOKEN) == null
                    )
            absent && configRepository.getCurrentBackendUrl() == origin
        }

    override suspend fun hasValidSession(): Boolean {
        if (getSession() != null) return true
        return loadCurrentSession(configRepository.getCurrentBackendUrl(), refreshMissing = true)
    }

    private suspend fun loadCurrentSession(
        origin: String,
        refreshMissing: Boolean = false,
    ): Boolean =
        sessionLoadMutex.withLock {
            while (true) {
                val expected = sessionState.value
                val decision = nativeRestorePolicy?.decision(origin)
                if (
                    expected.origin == origin &&
                    expected.restoreDecision == decision &&
                    (!refreshMissing || expected.session != null)
                ) {
                    return@withLock expected.session != null && configRepository.getCurrentBackendUrl() == origin
                }
                val loaded = loadSession(origin)
                if (nativeRestorePolicy?.decision(origin) != decision) continue
                if (configRepository.getCurrentBackendUrl() != origin) return@withLock false
                val current = sessionState.value
                if (current != expected) {
                    return@withLock (
                        current.origin == origin &&
                            current.session != null &&
                            configRepository.getCurrentBackendUrl() == origin
                    )
                }
                if (expected.origin != origin || expected.session != loaded || expected.restoreDecision != decision) {
                    sessionState.compareAndSet(expected, CachedSession(origin, loaded, expected.revision + 1, decision))
                }
                return@withLock getSession() != null
            }
            false
        }

    private fun cache(
        origin: String,
        session: UserSession?,
        restoreDecision: String?,
    ) {
        sessionState.update { CachedSession(origin, session, it.revision + 1, restoreDecision) }
    }

    override suspend fun saveSession(session: UserSession) {
        val origin = configRepository.getCurrentBackendUrl()
        writeSession(origin, session, false)
    }

    override suspend fun saveExplicitSession(session: UserSession) = writeSession(configRepository.getCurrentBackendUrl(), session, true)

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
                if (explicit) {
                    nativeRestorePolicy?.setAllowed(origin, true)
                    advanceAuthorization(origin)
                }
            } catch (error: Exception) {
                if (origin == configRepository.getCurrentBackendUrl()) sessionState.update { CachedSession(null, null, it.revision + 1) }
                invalidateConnectedCache(origin)
                throw error
            }
            if (origin == configRepository.getCurrentBackendUrl()) cache(origin, session, nativeRestorePolicy?.decision(origin))
        }
    }

    override suspend fun clear(origin: String) {
        mutateIdentity({ origin == configRepository.getCurrentBackendUrl() }) { clearLocked(origin) }
    }

    override suspend fun clearSessionIfCurrent(
        expected: OriginBoundSession,
        onCleared: () -> Unit,
    ): Boolean =
        mutateIdentity({ true }) {
            if (getOriginBoundSession() != expected ||
                secureStorage.getString(scopedSessionKey(SessionRecordKeys.RECORD, expected.origin)) !=
                encodeStoredSession(expected.session)
            ) {
                return@mutateIdentity false
            }
            clearLocked(expected.origin)
            onCleared()
            true
        }

    private suspend fun clearLocked(origin: String) {
        var restoreDecision = sessionState.value.restoreDecision
        try {
            nativeRestorePolicy?.setAllowed(origin, false)
            restoreDecision = nativeRestorePolicy?.decision(origin)
            // The secure tombstone also prevents legacy-token fallback after interrupted cleanup.
            secureStorage.putString(scopedSessionKey(SessionRecordKeys.RECORD, origin), "null")
        } finally {
            advanceAuthorization(origin)
            if (origin == configRepository.getCurrentBackendUrl()) cache(origin, null, restoreDecision)
        }
    }

    private fun authorizationRevision(origin: String): Long = authorizationRevisions.value[origin] ?: 0L

    private fun advanceAuthorization(origin: String) {
        authorizationRevisions.update { it + (origin to ((it[origin] ?: 0L) + 1)) }
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
                                host.cache(origin, after.session, after.restoreDecision)
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
            secureStorage.loadStoredSession(backendUrl) { nativeRestorePolicy?.canRestore(backendUrl) != false }
        }

    private suspend fun persistSession(
        origin: String,
        session: UserSession,
    ) = secureStorage.persistStoredSession(origin, session)
}
