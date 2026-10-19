package studio.hypertext.atproto.oauth.client

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import studio.hypertext.atproto.identity.AtprotoDid
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * When to renew a session's access token.
 */
public enum class OAuthRefreshMode {
    /** Renew only when the access token has expired or is about to. */
    AUTO,

    /** Renew now, even if the access token is still valid. */
    FORCE,

    /** Never renew; return the stored tokens as they are. */
    NEVER,
}

/**
 * Loads, renews, and ends stored sessions. Shared by [AtprotoOAuthClient] and [OAuthSession].
 */
internal class OAuthSessionManager(
    private val config: OAuthClientConfig,
    private val resolver: OAuthServerResolver,
    private val httpClient: HttpClient,
    private val crypto: OAuthCrypto,
    private val nonces: DpopNonceCache,
    private val sessionStore: OAuthSessionStore,
    private val sessionLock: OAuthSessionLock,
    private val clock: Clock,
) {
    suspend fun session(
        did: AtprotoDid,
        mode: OAuthRefreshMode,
    ): OAuthStoredSession =
        sessionLock.withLock(did) {
            val stored = sessionStore.get(did) ?: throw OAuthSessionNotFoundException(did)
            when (mode) {
                OAuthRefreshMode.NEVER -> stored
                OAuthRefreshMode.AUTO -> if (isStale(stored)) refresh(stored) else stored
                OAuthRefreshMode.FORCE -> refresh(stored)
            }
        }

    /**
     * Removes the session for [did] and throws [exception].
     */
    suspend fun end(
        did: AtprotoDid,
        exception: OAuthSessionEndedException,
    ): Nothing {
        sessionStore.delete(did)
        throw exception
    }

    /**
     * Revokes the session's tokens at its authorization server and removes it locally. The local
     * session is removed even when revocation fails.
     */
    suspend fun signOut(did: AtprotoDid) {
        sessionLock.withLock(did) {
            val stored = sessionStore.get(did) ?: return@withLock
            val failure =
                try {
                    agentFor(stored, resolver.authorizationServerMetadata(stored.issuer))
                        .revoke(stored.refreshToken ?: stored.accessToken)
                    null
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    exception
                } finally {
                    sessionStore.delete(did)
                }
            if (failure != null) throw OAuthRevocationException(did, failure)
        }
    }

    fun agentFor(
        metadata: OAuthAuthorizationServerMetadata,
        dpopKey: JsonWebKey,
        clientKey: OAuthClientKey,
    ): OAuthServerAgent =
        OAuthServerAgent(
            metadata = metadata,
            dpopKey = dpopKey,
            clientKey = clientKey,
            clientId = config.clientId,
            httpClient = httpClient,
            crypto = crypto,
            nonces = nonces,
            clock = clock,
        )

    private fun agentFor(
        stored: OAuthStoredSession,
        metadata: OAuthAuthorizationServerMetadata,
    ): OAuthServerAgent {
        val clientKey =
            config.key(stored.clientKeyId)
                ?: throw IllegalStateException("Client key ${stored.clientKeyId} that started this session is no longer configured")
        return agentFor(metadata, stored.dpopKey, clientKey)
    }

    private fun isStale(stored: OAuthStoredSession): Boolean {
        val expiresAt = stored.expiresAt ?: return false
        val margin = REFRESH_MARGIN + Random.nextLong(REFRESH_JITTER.inWholeMilliseconds).milliseconds
        return expiresAt < clock.now() + margin
    }

    private suspend fun refresh(stored: OAuthStoredSession): OAuthStoredSession {
        val did = stored.did
        val refreshToken = stored.refreshToken ?: return withoutRefreshToken(stored)
        val clientKey =
            config.key(stored.clientKeyId)
                ?: end(
                    did,
                    OAuthSessionRevokedException(did, "the client key ${stored.clientKeyId} that started it is no longer configured"),
                )

        val (pdsUrl, metadata) = resolver.resolveForDid(did)
        if (metadata.issuer != stored.issuer) {
            val mismatch = OAuthIssuerMismatchException(stored.issuer, metadata.issuer)
            end(did, OAuthSessionRevokedException(did, "the account's authorization server changed", mismatch))
        }

        val tokens =
            try {
                agentFor(metadata, stored.dpopKey, clientKey).refresh(refreshToken)
            } catch (exception: OAuthServerResponseException) {
                if (exception.error != "invalid_grant") throw exception
                return recoverFromRejectedRefresh(stored, exception)
            }

        if (tokens.sub != did.toString()) {
            val mismatch = OAuthSubjectMismatchException(did, tokens.sub)
            end(did, OAuthSessionRevokedException(did, "the refreshed tokens belong to ${tokens.sub}", mismatch))
        }

        val renewed =
            stored.copy(
                pdsUrl = pdsUrl,
                scope = tokens.scope,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken ?: refreshToken,
                expiresAt = tokens.expiresAt(clock.now()),
            )
        sessionStore.save(renewed)
        return renewed
    }

    private suspend fun withoutRefreshToken(stored: OAuthStoredSession): OAuthStoredSession {
        if (!stored.isExpired(clock.now())) return stored
        end(stored.did, OAuthSessionExpiredException(stored.did, "the access token expired and no refresh token was issued"))
    }

    /**
     * Handles `invalid_grant` from a refresh. If another instance refreshed the session in the
     * meantime, its newer tokens are in the store and are returned. Otherwise the session has ended.
     */
    private suspend fun recoverFromRejectedRefresh(
        stored: OAuthStoredSession,
        exception: OAuthServerResponseException,
    ): OAuthStoredSession {
        val did = stored.did
        val latest = sessionStore.get(did)
        if (latest != null && (latest.accessToken != stored.accessToken || latest.refreshToken != stored.refreshToken)) {
            return latest
        }
        val detail = exception.errorDescription ?: "the authorization server rejected the refresh token"
        if (detail.contains("expired", ignoreCase = true)) end(did, OAuthSessionExpiredException(did, detail, exception))
        end(did, OAuthSessionRevokedException(did, detail, exception))
    }

    private companion object {
        val REFRESH_MARGIN = 10.seconds
        val REFRESH_JITTER = 30.seconds
    }
}

internal fun TokenResponse.expiresAt(now: Instant): Instant? = expiresInSeconds?.let { now + it.seconds }

internal fun OAuthStoredSession.isExpired(now: Instant): Boolean = expiresAt?.let { it <= now } ?: false
