package app.logdate.server.auth

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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

    override fun generateSessionToken(sessionId: String): String {
        val now = Clock.System.now()
        val expiresAt = now + sessionTokenExpiration

        val tokenData = "session.$sessionId.${expiresAt.epochSeconds}"
        return "memory_${tokenData.hashCode().toString(16)}_$tokenData"
    }

    override fun validateAccessToken(token: String): String? = validateToken(token, "access")

    override fun validateRefreshToken(token: String): String? = validateToken(token, "refresh")

    override fun validateSessionToken(token: String): String? =
        validateToken(token, "session")?.let {
            // For session tokens, return the session ID instead of account ID.
            token.substringAfter("session.").substringBefore(".")
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
        val expiresAt = kotlin.time.Instant.fromEpochSeconds(expiresAtSeconds)
        if (now > expiresAt) return null

        return accountId
    }
}
