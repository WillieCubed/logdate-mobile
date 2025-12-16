package app.logdate.server.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import java.security.SecureRandom

@OptIn(ExperimentalUuidApi::class)
interface SessionManager {
    suspend fun storeSession(session: TemporarySession): String
    suspend fun getSession(sessionId: String): TemporarySession?
    suspend fun markSessionUsed(sessionId: String): Boolean
    suspend fun removeSession(sessionId: String): Boolean
    suspend fun cleanupExpiredSessions(): Int
    
    /**
     * Create a new temporary session for account creation.
     */
    suspend fun createAccountCreationSession(
        username: String,
        displayName: String,
        challenge: String,
        deviceInfo: DeviceInfo? = null,
        bio: String? = null
    ): TemporarySession
    
    /**
     * Create a new temporary session for authentication.
     */
    suspend fun createAuthenticationSession(
        challenge: String,
        accountHint: String? = null,
        deviceInfo: DeviceInfo? = null
    ): TemporarySession
    
    /**
     * Validate that a session exists, is not expired, and is not already used.
     */
    suspend fun validateSession(sessionId: String, expectedType: SessionType? = null): TemporarySession?
}

@OptIn(ExperimentalUuidApi::class)
class InMemorySessionManager : SessionManager {
    private val sessions = mutableMapOf<String, TemporarySession>()
    private val secureRandom = SecureRandom()
    
    companion object {
        private val SESSION_DURATION = 15.minutes
    }
    
    override suspend fun storeSession(session: TemporarySession): String {
        sessions[session.id] = session
        return session.id
    }
    
    override suspend fun getSession(sessionId: String): TemporarySession? {
        val session = sessions[sessionId] ?: return null
        
        // Check if session is expired
        if (Clock.System.now() > session.expiresAt) {
            sessions.remove(sessionId)
            return null
        }
        
        return session
    }
    
    override suspend fun markSessionUsed(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        sessions[sessionId] = session.copy(isUsed = true)
        return true
    }
    
    override suspend fun removeSession(sessionId: String): Boolean {
        return sessions.remove(sessionId) != null
    }
    
    override suspend fun cleanupExpiredSessions(): Int {
        val now = Clock.System.now()
        val expiredSessions = sessions.filter { (_, session) ->
            now > session.expiresAt || session.isUsed
        }
        
        expiredSessions.keys.forEach { sessionId ->
            sessions.remove(sessionId)
        }
        
        return expiredSessions.size
    }
    
    override suspend fun createAccountCreationSession(
        username: String,
        displayName: String,
        challenge: String,
        deviceInfo: DeviceInfo?,
        bio: String?
    ): TemporarySession {
        val sessionId = generateSessionId()
        val temporaryUserId = Uuid.random()
        val now = Clock.System.now()
        
        val session = TemporarySession(
            id = sessionId,
            temporaryUserId = temporaryUserId,
            challenge = challenge,
            username = username,
            deviceInfo = deviceInfo,
            sessionType = SessionType.ACCOUNT_CREATION,
            createdAt = now,
            expiresAt = now + SESSION_DURATION,
            isUsed = false
        )
        
        sessions[sessionId] = session
        return session
    }
    
    override suspend fun createAuthenticationSession(
        challenge: String,
        accountHint: String?,
        deviceInfo: DeviceInfo?
    ): TemporarySession {
        val sessionId = generateSessionId()
        val temporaryUserId = Uuid.random()
        val now = Clock.System.now()
        
        val session = TemporarySession(
            id = sessionId,
            temporaryUserId = temporaryUserId,
            challenge = challenge,
            username = accountHint ?: "",
            deviceInfo = deviceInfo,
            sessionType = SessionType.AUTHENTICATION,
            createdAt = now,
            expiresAt = now + SESSION_DURATION,
            isUsed = false
        )
        
        sessions[sessionId] = session
        return session
    }
    
    override suspend fun validateSession(sessionId: String, expectedType: SessionType?): TemporarySession? {
        val session = getSession(sessionId) ?: return null
        
        // Check if session is already used
        if (session.isUsed) {
            return null
        }
        
        // Check session type if specified
        if (expectedType != null && session.sessionType != expectedType) {
            return null
        }
        
        return session
    }
    
    private fun generateSessionId(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}