package app.logdate.client.datastore

/**
 * Data class representing a user session
 */
data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val accountId: String,
)

/** An atomic credential snapshot; the origin must travel with the session, never be inferred later. */
data class OriginBoundSession(
    val origin: String,
    val session: UserSession,
)

/** Opaque authorization retained by its caller independently of the vault's bounded guard cache. */
interface SessionAuthorizationToken

/** A vault revision that prevents an in-flight sign-in from replacing a later identity decision. */
data class SessionInstallationGuard(
    val origin: String,
    val revision: Long,
    val token: String? = null,
    val authorization: SessionAuthorizationToken? = null,
)

/** A session and its opaque authorization guard captured under one identity lock. */
data class SessionAuthenticationSnapshot(
    val session: OriginBoundSession?,
    val guard: SessionInstallationGuard,
)

/**
 * Interface for session storage
 */
interface SessionStorage {
    val supportsGuardedAuthentication: Boolean get() = false

    suspend fun captureAuthenticationSnapshot(): SessionAuthenticationSnapshot? = null

    /** Token refresh and passive recovery do not supersede a captured explicit authorization. */
    suspend fun isAuthenticationGuardCurrent(expected: SessionInstallationGuard): Boolean = false

    suspend fun captureAuthenticationGuard(): SessionInstallationGuard? = null

    suspend fun installExplicitSessionIfCurrent(
        expected: SessionInstallationGuard,
        session: UserSession,
    ): Boolean = false

    suspend fun saveExplicitSession(session: UserSession) = saveSession(session)

    suspend fun clearSessionIfCurrent(
        expected: OriginBoundSession,
        onCleared: () -> Unit = {},
    ): Boolean = false

    /** Commits sign-out only if no later authentication or storage decision superseded this guard. */
    suspend fun clearSessionIfUnchanged(
        expected: SessionInstallationGuard,
        onCleared: () -> Unit = {},
    ): Boolean = false

    /** Publishes account state only while the captured login still owns the session. */
    suspend fun runIfSessionCurrent(
        expected: OriginBoundSession?,
        action: () -> Unit,
    ): Boolean {
        val binding = getOriginBoundSession()
        val current = binding?.session ?: getSession()
        val matches =
            if (expected == null) {
                current == null
            } else {
                current?.accountId == expected.session.accountId &&
                    current.refreshToken == expected.session.refreshToken &&
                    (binding == null || binding.origin == expected.origin)
            }
        if (supportsGuardedAuthentication || !matches) return false
        action()
        return true
    }

    suspend fun captureInstallationGuard(): SessionInstallationGuard? = null

    suspend fun installSessionIfCurrent(
        expected: SessionInstallationGuard,
        session: UserSession,
    ): Boolean = false

    /**
     * Gets the current session synchronously from the latest cached value.
     */
    fun getSession(): UserSession?

    /** Implementations without origin provenance cannot authorize destination-bound requests. */
    fun getOriginBoundSession(): OriginBoundSession? = null

    /** Conditional refresh must persist to the captured origin, even if configuration changes next. */
    suspend fun replaceSessionIfCurrent(
        expected: OriginBoundSession,
        updated: UserSession,
    ): Boolean = false

    /** Refresh retains the authorization captured before the request, including identical-token logins. */
    suspend fun replaceSessionIfAuthorized(
        expected: OriginBoundSession,
        updated: UserSession,
        authorization: SessionInstallationGuard?,
    ): Boolean = authorization == null && replaceSessionIfCurrent(expected, updated)

    /**
     * Observes session changes as a Flow.
     * Emits null when no session exists, UserSession when authenticated.
     */
    fun getSessionFlow(): kotlinx.coroutines.flow.Flow<UserSession?>

    /** Strict durable absence check for destructive provider cleanup; read failures must propagate. */
    suspend fun isSignedOutForProviderCleanup(): Boolean = false

    /** Checks if a valid session exists. */
    suspend fun hasValidSession(): Boolean

    /**
     * Saves a new session.
     */
    suspend fun saveSession(session: UserSession)

    /**
     * Clears the current session.
     */
    suspend fun clearSession()
}
