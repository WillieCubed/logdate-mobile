package app.logdate.server.auth

import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Production JWT token service for LogDate Cloud authentication.
 * 
 * This implementation generates and validates JWT tokens using HMAC-SHA256.
 * Tokens are structured as standard JWT with header, payload, and signature.
 */
class JwtTokenService(
    private val secret: String = generateSecret(),
    private val issuer: String = "logdate.app",
    private val audience: String = "logdate-api"
) : TokenService {
    
    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private val ACCESS_TOKEN_DURATION = 1.hours
        private val REFRESH_TOKEN_DURATION = 30.days
        private val SESSION_TOKEN_DURATION = 15.minutes
        
        fun generateSecret(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getEncoder().encodeToString(bytes)
        }
    }
    
    @Serializable
    private data class JwtHeader(
        val alg: String = "HS256",
        val typ: String = "JWT"
    )
    
    @Serializable
    private data class JwtPayload(
        val sub: String, // subject (account ID or session ID)
        val iss: String, // issuer
        val aud: String, // audience
        val exp: Long,   // expiration time (seconds since epoch)
        val iat: Long,   // issued at (seconds since epoch)
        val type: String // token type: "access", "refresh", "session"
    )
    
    override fun generateAccessToken(accountId: String): String {
        val now = Clock.System.now()
        val expiry = now + ACCESS_TOKEN_DURATION
        
        val payload = JwtPayload(
            sub = accountId,
            iss = issuer,
            aud = audience,
            exp = expiry.epochSeconds,
            iat = now.epochSeconds,
            type = "access"
        )
        
        return generateToken(payload)
    }
    
    override fun generateRefreshToken(accountId: String): String {
        val now = Clock.System.now()
        val expiry = now + REFRESH_TOKEN_DURATION
        
        val payload = JwtPayload(
            sub = accountId,
            iss = issuer,
            aud = audience,
            exp = expiry.epochSeconds,
            iat = now.epochSeconds,
            type = "refresh"
        )
        
        return generateToken(payload)
    }
    
    override fun generateSessionToken(sessionId: String): String {
        val now = Clock.System.now()
        val expiry = now + SESSION_TOKEN_DURATION
        
        val payload = JwtPayload(
            sub = sessionId,
            iss = issuer,
            aud = audience,
            exp = expiry.epochSeconds,
            iat = now.epochSeconds,
            type = "session"
        )
        
        return generateToken(payload)
    }
    
    override fun validateAccessToken(token: String): String? {
        return validateToken(token, "access")
    }
    
    override fun validateRefreshToken(token: String): String? {
        return validateToken(token, "refresh")
    }
    
    override fun validateSessionToken(token: String): String? {
        return validateToken(token, "session")
    }
    
    private fun generateToken(payload: JwtPayload): String {
        try {
            val header = JwtHeader()
            
            // Encode header and payload
            val encodedHeader = base64UrlEncode(Json.encodeToString(header))
            val encodedPayload = base64UrlEncode(Json.encodeToString(payload))
            
            // Create signature
            val message = "$encodedHeader.$encodedPayload"
            val signature = createSignature(message)
            
            return "$message.$signature"
        } catch (e: Exception) {
            Napier.e("Failed to generate JWT token", e)
            throw IllegalStateException("Failed to generate token", e)
        }
    }
    
    private fun validateToken(token: String, expectedType: String): String? {
        try {
            val parts = token.split(".")
            if (parts.size != 3) {
                Napier.w("Invalid JWT format: expected 3 parts, got ${parts.size}")
                return null
            }
            
            val (encodedHeader, encodedPayload, providedSignature) = parts
            
            // Verify signature
            val message = "$encodedHeader.$encodedPayload"
            val expectedSignature = createSignature(message)
            
            if (providedSignature != expectedSignature) {
                Napier.w("JWT signature verification failed")
                return null
            }
            
            // Decode and validate payload
            val payloadJson = base64UrlDecode(encodedPayload)
            val payload = Json.decodeFromString<JwtPayload>(payloadJson)
            
            // Validate token type
            if (payload.type != expectedType) {
                Napier.w("JWT type mismatch: expected $expectedType, got ${payload.type}")
                return null
            }
            
            // Validate issuer and audience
            if (payload.iss != issuer || payload.aud != audience) {
                Napier.w("JWT issuer/audience validation failed")
                return null
            }
            
            // Check expiration
            val now = Clock.System.now().epochSeconds
            if (payload.exp <= now) {
                Napier.d("JWT token expired: exp=${payload.exp}, now=$now")
                return null
            }
            
            return payload.sub
        } catch (e: Exception) {
            Napier.e("Failed to validate JWT token", e)
            return null
        }
    }
    
    private fun createSignature(message: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM)
        mac.init(secretKey)
        val signature = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return base64UrlEncode(signature)
    }
    
    private fun base64UrlEncode(data: String): String {
        return base64UrlEncode(data.toByteArray(StandardCharsets.UTF_8))
    }
    
    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(data)
    }
    
    private fun base64UrlDecode(data: String): String {
        val decoded = Base64.getUrlDecoder().decode(data)
        return String(decoded, StandardCharsets.UTF_8)
    }
}