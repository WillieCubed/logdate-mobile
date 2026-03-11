package app.logdate.server.oauth

import kotlin.time.Instant

/**
 * Durable storage boundary for OAuth runtime state used by the authorization server.
 *
 * Single-node production needs these records to survive restarts even if the server process
 * restarts between PAR, code exchange, refresh, and revoke operations.
 */
interface OAuthRuntimeStateRepository {
    suspend fun saveAuthorizationRequest(request: StoredAuthorizationRequest): StoredAuthorizationRequest

    suspend fun findAuthorizationRequest(requestUri: String): StoredAuthorizationRequest?

    suspend fun deleteAuthorizationRequest(requestUri: String): Boolean

    suspend fun saveAuthorizationCode(code: StoredAuthorizationCode): StoredAuthorizationCode

    suspend fun takeAuthorizationCode(code: String): StoredAuthorizationCode?

    suspend fun saveRefreshToken(token: StoredRefreshToken): StoredRefreshToken

    suspend fun findRefreshToken(token: String): StoredRefreshToken?

    suspend fun revokeRefreshToken(
        token: String,
        revokedAt: Instant,
    ): Boolean

    suspend fun deleteRefreshToken(token: String): Boolean
}

data class StoredAuthorizationRequest(
    val requestUri: String,
    val clientId: String,
    val clientName: String,
    val redirectUri: String,
    val scope: String,
    val state: String?,
    val loginHint: String?,
    val codeChallenge: String,
    val dpopKeyThumbprint: String,
    val clientAuthKeyId: String?,
    val clientAuthKeyThumbprint: String?,
    val expiresAt: Instant,
)

data class StoredAuthorizationCode(
    val code: String,
    val clientId: String,
    val redirectUri: String,
    val subjectDid: String,
    val subjectHandle: String,
    val scope: String,
    val codeChallenge: String,
    val dpopKeyThumbprint: String,
    val clientAuthKeyId: String?,
    val clientAuthKeyThumbprint: String?,
    val expiresAt: Instant,
)

data class StoredRefreshToken(
    val token: String,
    val clientId: String,
    val subjectDid: String,
    val scope: String,
    val dpopKeyThumbprint: String,
    val clientAuthKeyId: String?,
    val clientAuthKeyThumbprint: String?,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
)

class InMemoryOAuthRuntimeStateRepository : OAuthRuntimeStateRepository {
    private val requestUris = linkedMapOf<String, StoredAuthorizationRequest>()
    private val authorizationCodes = linkedMapOf<String, StoredAuthorizationCode>()
    private val refreshTokens = linkedMapOf<String, StoredRefreshToken>()

    override suspend fun saveAuthorizationRequest(request: StoredAuthorizationRequest): StoredAuthorizationRequest {
        requestUris[request.requestUri] = request
        return request
    }

    override suspend fun findAuthorizationRequest(requestUri: String): StoredAuthorizationRequest? = requestUris[requestUri]

    override suspend fun deleteAuthorizationRequest(requestUri: String): Boolean = requestUris.remove(requestUri) != null

    override suspend fun saveAuthorizationCode(code: StoredAuthorizationCode): StoredAuthorizationCode {
        authorizationCodes[code.code] = code
        return code
    }

    override suspend fun takeAuthorizationCode(code: String): StoredAuthorizationCode? = authorizationCodes.remove(code)

    override suspend fun saveRefreshToken(token: StoredRefreshToken): StoredRefreshToken {
        refreshTokens[token.token] = token
        return token
    }

    override suspend fun findRefreshToken(token: String): StoredRefreshToken? = refreshTokens[token]

    override suspend fun revokeRefreshToken(
        token: String,
        revokedAt: Instant,
    ): Boolean {
        val existing = refreshTokens[token] ?: return false
        refreshTokens[token] = existing.copy(revokedAt = revokedAt)
        return true
    }

    override suspend fun deleteRefreshToken(token: String): Boolean = refreshTokens.remove(token) != null
}
