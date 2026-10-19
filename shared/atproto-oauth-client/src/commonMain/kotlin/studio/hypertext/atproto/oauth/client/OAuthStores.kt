package studio.hypertext.atproto.oauth.client

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import studio.hypertext.atproto.identity.AtprotoDid
import kotlin.time.Instant

/**
 * What the client remembers about a sign-in between sending the person to the authorization
 * server and handling the callback.
 *
 * Treat this as secret: it contains the PKCE verifier and the session's private DPoP key.
 *
 * @property state The random `state` value sent with the authorization request.
 * @property issuer The authorization server the request was sent to.
 * @property dpopKey The private DPoP key bound to this sign-in.
 * @property clientKeyId The client key that authenticated the request. The session must keep it.
 * @property pkceVerifier The PKCE code verifier.
 * @property redirectUri The redirect URI sent with the request.
 * @property expectedDid The account the sign-in started for, or `null` when it started from a
 *   server URL.
 * @property appState Opaque application data passed to [AtprotoOAuthClient.authorize].
 * @property createdAt When the authorization request was created.
 */
@Serializable
public data class OAuthAuthorizationState(
    val state: String,
    val issuer: String,
    val dpopKey: JsonWebKey,
    val clientKeyId: String,
    val pkceVerifier: String,
    val redirectUri: String,
    val expectedDid: AtprotoDid?,
    val appState: String?,
    val createdAt: Instant,
) {
    override fun toString(): String = "OAuthAuthorizationState(issuer=$issuer, expectedDid=$expectedDid, createdAt=$createdAt)"
}

/**
 * Everything needed to use and renew an OAuth session, in a form any server instance can load.
 *
 * Treat this as secret: it contains the tokens and the session's private DPoP key.
 *
 * @property did The account the session authorizes.
 * @property issuer The authorization server that issued the tokens.
 * @property pdsUrl The account's data server (the resource server the tokens are for).
 * @property scope The scopes the person granted, space-separated.
 * @property accessToken The DPoP-bound access token.
 * @property refreshToken The refresh token, if the server issued one.
 * @property expiresAt When the access token expires, if the server said.
 * @property dpopKey The private DPoP key the tokens are bound to.
 * @property clientKeyId The client key the session was started with.
 */
@Serializable
public data class OAuthStoredSession(
    val did: AtprotoDid,
    val issuer: String,
    val pdsUrl: String,
    val scope: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant?,
    val dpopKey: JsonWebKey,
    val clientKeyId: String,
) {
    override fun toString(): String = "OAuthStoredSession(did=$did, issuer=$issuer, pdsUrl=$pdsUrl, scope=$scope, expiresAt=$expiresAt)"
}

/**
 * Persists pending authorization requests between [AtprotoOAuthClient.authorize] and
 * [AtprotoOAuthClient.callback].
 *
 * The callback may arrive at a different server instance than the one that started the sign-in,
 * so production implementations should use shared storage.
 */
public interface OAuthStateStore {
    /**
     * Saves [value] under its `state`.
     */
    public suspend fun save(value: OAuthAuthorizationState)

    /**
     * Returns and removes the entry for [state] in one step, so each state can be used once.
     */
    public suspend fun take(state: String): OAuthAuthorizationState?
}

/**
 * Persists OAuth sessions, keyed by account DID.
 *
 * Refresh tokens are single-use, so [save] must replace the previous entry and every instance must
 * read the latest value through [get].
 */
public interface OAuthSessionStore {
    /**
     * Returns the session for [did], or `null` if there is none.
     */
    public suspend fun get(did: AtprotoDid): OAuthStoredSession?

    /**
     * Saves [session], replacing any previous session for the same DID.
     */
    public suspend fun save(session: OAuthStoredSession)

    /**
     * Removes the session for [did], if there is one.
     */
    public suspend fun delete(did: AtprotoDid)
}

/**
 * Serializes token refreshes for one account.
 *
 * Refresh tokens are single-use: if two refreshes of the same session run at once, one fails and
 * the authorization server may end the session. The default [InProcessOAuthSessionLock] covers one
 * process. When several server instances share a session store, supply a lock that spans them,
 * such as a database advisory lock. Without one, the client still recovers by re-reading the
 * session store when a refresh is rejected, but a distributed lock avoids the race.
 */
public interface OAuthSessionLock {
    /**
     * Runs [block] while holding the lock for [did].
     */
    public suspend fun <T> withLock(
        did: AtprotoDid,
        block: suspend () -> T,
    ): T
}

/**
 * An [OAuthSessionLock] that serializes refreshes within the current process.
 */
public class InProcessOAuthSessionLock : OAuthSessionLock {
    private val guard = Mutex()
    private val locks = mutableMapOf<AtprotoDid, Mutex>()

    override suspend fun <T> withLock(
        did: AtprotoDid,
        block: suspend () -> T,
    ): T {
        val lock = guard.withLock { locks.getOrPut(did) { Mutex() } }
        return lock.withLock { block() }
    }
}

/**
 * An in-memory [OAuthStateStore] for tests and single-process development servers.
 */
public class InMemoryOAuthStateStore : OAuthStateStore {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, OAuthAuthorizationState>()

    override suspend fun save(value: OAuthAuthorizationState) {
        mutex.withLock { entries[value.state] = value }
    }

    override suspend fun take(state: String): OAuthAuthorizationState? = mutex.withLock { entries.remove(state) }
}

/**
 * An in-memory [OAuthSessionStore] for tests and single-process development servers.
 */
public class InMemoryOAuthSessionStore : OAuthSessionStore {
    private val mutex = Mutex()
    private val entries = mutableMapOf<AtprotoDid, OAuthStoredSession>()

    override suspend fun get(did: AtprotoDid): OAuthStoredSession? = mutex.withLock { entries[did] }

    override suspend fun save(session: OAuthStoredSession) {
        mutex.withLock { entries[session.did] = session }
    }

    override suspend fun delete(did: AtprotoDid) {
        mutex.withLock { entries.remove(did) }
    }
}
