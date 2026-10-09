package app.logdate.server.auth

import app.logdate.shared.model.AccountTokens
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Deterministic [TokenService] for local tests and in-memory server fixtures.
 */
class InMemoryTokenService(
    private val issuer: String = "logdate.app",
) : TokenService {
    // Token expiration times
    private val accessTokenExpiration = 1.hours
    private val refreshTokenExpiration = 720.hours // 30 days
    private val sessionTokenExpiration = 15.minutes

    // The readable test format has no room for a session or scope, so device tokens are looked up here.
    private val deviceTokens = ConcurrentHashMap<String, AccessTokenClaims>()

    override fun generateAccessToken(
        accountId: String,
        did: String?,
    ): String {
        val now = Clock.System.now()
        val expiresAt = now + accessTokenExpiration

        // Simple token format: type.accountId.expiresAt.hash
        val tokenData = "access.$accountId.${expiresAt.epochSeconds}"
        return "memory_${tokenData.hashCode().toString(16)}_$tokenData"
    }

    override fun generateRefreshToken(
        accountId: String,
        did: String?,
    ): String {
        val now = Clock.System.now()
        val expiresAt = now + refreshTokenExpiration

        val tokenData = "refresh.$accountId.${expiresAt.epochSeconds}"
        return "memory_${tokenData.hashCode().toString(16)}_$tokenData"
    }

    override fun generateEnrollmentTokens(
        accountId: String,
        did: String?,
        enrollmentId: String,
        issuedAt: Instant,
    ): AccountTokens {
        fun token(
            type: String,
            expiry: Instant,
        ): String {
            val data = "$type.$accountId.${expiry.epochSeconds}"
            return "memory_${("$enrollmentId:$data").hashCode().toString(16)}_$data"
        }
        return AccountTokens(
            token("access", issuedAt + accessTokenExpiration),
            token("refresh", issuedAt + refreshTokenExpiration),
        )
    }

    override fun generateDeviceAccessToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String = deviceToken("access", accountId, did, sessionId, accessTokenExpiration)

    override fun generateDeviceRefreshToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String = deviceToken("refresh", accountId, did, sessionId, refreshTokenExpiration)

    override fun generateSessionToken(sessionId: String): String {
        val now = Clock.System.now()
        val expiresAt = now + sessionTokenExpiration

        val tokenData = "session.$sessionId.${expiresAt.epochSeconds}"
        return "memory_${tokenData.hashCode().toString(16)}_$tokenData"
    }

    // ConcurrentHashMap's `in` is the legacy contains(value); containsKey is the check that matters here.
    override fun validateAccessToken(token: String): String? =
        validateToken(token, "access")?.takeUnless { deviceTokens.containsKey(token) }

    override fun validateRefreshToken(token: String): String? =
        validateToken(token, "refresh")?.takeUnless { deviceTokens.containsKey(token) }

    override fun decodeAccessToken(token: String): AccessTokenClaims? = decode(token, "access")

    override fun decodeRefreshToken(token: String): AccessTokenClaims? = decode(token, "refresh")

    override fun validateSessionToken(token: String): String? =
        validateToken(token, "session")?.let {
            // For session tokens, return the session ID instead of account ID.
            token.substringAfter("session.").substringBefore(".")
        }

    private fun decode(
        token: String,
        type: String,
    ): AccessTokenClaims? {
        val accountId = validateToken(token, type) ?: return null
        return deviceTokens[token] ?: AccessTokenClaims.full(accountId)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun deviceToken(
        type: String,
        accountId: String,
        did: String?,
        sessionId: String,
        lifetime: Duration,
    ): String {
        val expiresAt = Clock.System.now() + lifetime
        val token = "memory_${Uuid.random().toHexString()}_$type.$accountId.${expiresAt.epochSeconds}"
        deviceTokens[token] = AccessTokenClaims(accountId, did, sessionId, TokenScope.SYNC)
        return token
    }

    private fun validateToken(
        token: String,
        expectedType: String,
    ): String? {
        if (!token.startsWith("memory_")) return null

        val tokenData = token.substringAfter("memory_").substringAfter("_")
        val parts = tokenData.split(".")

        if (parts.size != 3) return null

        val type = parts[0]
        val accountId = parts[1]
        val expiresAtSeconds = parts[2].toLongOrNull() ?: return null

        if (type != expectedType) return null

        val now = Clock.System.now()
        val expiresAt = Instant.fromEpochSeconds(expiresAtSeconds)
        if (now > expiresAt) return null

        return accountId
    }
}
