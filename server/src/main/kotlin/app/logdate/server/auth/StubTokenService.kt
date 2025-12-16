package app.logdate.server.auth

import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Stub implementation of TokenService for development and testing.
 * 
 * This implementation generates simple tokens without actual JWT signing.
 * Should be replaced with proper JWT implementation in production.
 */
class StubTokenService(
    private val issuer: String = "logdate.app"
) : TokenService {
    
    // Token expiration times
    private val accessTokenExpiration = 1.hours
    private val refreshTokenExpiration = 720.hours // 30 days
    private val sessionTokenExpiration = 15.minutes
    
    override fun generateAccessToken(accountId: String): String {
        val now = Clock.System.now()
        val expiresAt = now + accessTokenExpiration
        
        // Simple token format: type.accountId.expiresAt.hash
        val tokenData = "access.$accountId.${expiresAt.epochSeconds}"
        return "stub_${tokenData.hashCode().toString(16)}_$tokenData"
    }
    
    override fun generateRefreshToken(accountId: String): String {
        val now = Clock.System.now()
        val expiresAt = now + refreshTokenExpiration
        
        val tokenData = "refresh.$accountId.${expiresAt.epochSeconds}"
        return "stub_${tokenData.hashCode().toString(16)}_$tokenData"
    }
    
    override fun generateSessionToken(sessionId: String): String {
        val now = Clock.System.now()
        val expiresAt = now + sessionTokenExpiration
        
        val tokenData = "session.$sessionId.${expiresAt.epochSeconds}"
        return "stub_${tokenData.hashCode().toString(16)}_$tokenData"
    }
    
    override fun validateAccessToken(token: String): String? {
        return validateToken(token, "access")
    }
    
    override fun validateRefreshToken(token: String): String? {
        return validateToken(token, "refresh")
    }
    
    override fun validateSessionToken(token: String): String? {
        return validateToken(token, "session")?.let { accountId ->
            // For session tokens, return the session ID instead of account ID
            if (token.contains("session.")) {
                token.substringAfter("session.").substringBefore(".")
            } else {
                accountId
            }
        }
    }
    
    private fun validateToken(token: String, expectedType: String): String? {
        if (!token.startsWith("stub_")) return null
        
        try {
            val tokenData = token.substringAfter("stub_").substringAfter("_")
            val parts = tokenData.split(".")
            
            if (parts.size != 3) return null
            
            val type = parts[0]
            val accountId = parts[1]
            val expiresAtSeconds = parts[2].toLongOrNull() ?: return null
            
            if (type != expectedType) return null
            
            val now = Clock.System.now()
            val expiresAt = kotlinx.datetime.Instant.fromEpochSeconds(expiresAtSeconds)
            
            if (now > expiresAt) return null
            
            return accountId
        } catch (e: Exception) {
            return null
        }
    }
}