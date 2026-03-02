package app.logdate.server.database

import app.logdate.server.auth.DeviceInfo
import app.logdate.server.auth.SessionManager
import app.logdate.server.auth.SessionType
import app.logdate.server.auth.TemporarySession
import app.logdate.server.util.toKotlinxInstant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLSessionManager : SessionManager {
    private val secureRandom = SecureRandom()

    companion object {
        private val SESSION_DURATION = 15.minutes
    }

    override suspend fun storeSession(session: TemporarySession): String {
        transaction {
            SessionsTable.insert {
                it[id] = session.id
                it[temporaryUserId] = session.temporaryUserId.toJavaUUID()
                it[challenge] = session.challenge
                it[sessionType] = session.sessionType.name
                it[username] = session.username
                it[displayName] = session.displayName
                it[bio] = session.bio
                it[deviceInfo] = session.deviceInfo?.toString()
                it[createdAt] = session.createdAt.toKotlinxInstant()
                it[expiresAt] = session.expiresAt.toKotlinxInstant()
                it[isUsed] = session.isUsed
            }
        }
        return session.id
    }

    override suspend fun getSession(sessionId: String): TemporarySession? {
        return transaction {
            val sessionRow =
                SessionsTable
                    .selectAll()
                    .where { SessionsTable.id eq sessionId }
                    .singleOrNull()
                    ?: return@transaction null

            val session = sessionRow.toTemporarySession()

            // Check if session is expired
            if (Clock.System.now() > session.expiresAt) {
                // Remove expired session
                SessionsTable.deleteWhere { id eq sessionId }
                return@transaction null
            }

            session
        }
    }

    override suspend fun markSessionUsed(sessionId: String): Boolean =
        transaction {
            val updatedRows =
                SessionsTable.update({ SessionsTable.id eq sessionId }) {
                    it[isUsed] = true
                }
            updatedRows > 0
        }

    override suspend fun removeSession(sessionId: String): Boolean =
        transaction {
            val deletedRows = SessionsTable.deleteWhere { id eq sessionId }
            deletedRows > 0
        }

    override suspend fun cleanupExpiredSessions(): Int =
        transaction {
            val now = Clock.System.now()

            // Delete expired or used sessions
            val deletedRows =
                SessionsTable.deleteWhere {
                    (expiresAt less now) or (isUsed eq true)
                }

            deletedRows
        }

    override suspend fun createAccountCreationSession(
        temporaryUserId: Uuid?,
        username: String,
        displayName: String,
        challenge: String,
        deviceInfo: DeviceInfo?,
        bio: String?,
    ): TemporarySession {
        val sessionId = generateSessionId()
        val resolvedUserId = temporaryUserId ?: Uuid.random()
        val now = Clock.System.now()

        val session =
            TemporarySession(
                id = sessionId,
                temporaryUserId = resolvedUserId,
                challenge = challenge,
                username = username,
                displayName = displayName,
                bio = bio,
                deviceInfo = deviceInfo,
                sessionType = SessionType.ACCOUNT_CREATION,
                createdAt = now,
                expiresAt = now + SESSION_DURATION,
                isUsed = false,
            )

        storeSession(session)
        return session
    }

    override suspend fun createAuthenticationSession(
        challenge: String,
        accountHint: String?,
        deviceInfo: DeviceInfo?,
    ): TemporarySession {
        val sessionId = generateSessionId()
        val temporaryUserId = Uuid.random()
        val now = Clock.System.now()

        val session =
            TemporarySession(
                id = sessionId,
                temporaryUserId = temporaryUserId,
                challenge = challenge,
                username = accountHint ?: "",
                displayName = "",
                bio = null,
                deviceInfo = deviceInfo,
                sessionType = SessionType.AUTHENTICATION,
                createdAt = now,
                expiresAt = now + SESSION_DURATION,
                isUsed = false,
            )

        storeSession(session)
        return session
    }

    override suspend fun validateSession(
        sessionId: String,
        expectedType: SessionType?,
    ): TemporarySession? {
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

    private fun ResultRow.toTemporarySession(): TemporarySession =
        TemporarySession(
            id = this[SessionsTable.id],
            temporaryUserId = this[SessionsTable.temporaryUserId].toKotlinUuid(),
            challenge = this[SessionsTable.challenge],
            username = this[SessionsTable.username] ?: "",
            displayName = this[SessionsTable.displayName] ?: "",
            bio = this[SessionsTable.bio],
            deviceInfo =
                this[SessionsTable.deviceInfo]?.let {
                    // Parse device info JSON if needed
                    null // For now, keeping it simple
                },
            sessionType = SessionType.valueOf(this[SessionsTable.sessionType]),
            createdAt = this[SessionsTable.createdAt].toKotlinxInstant(),
            expiresAt = this[SessionsTable.expiresAt].toKotlinxInstant(),
            isUsed = this[SessionsTable.isUsed],
        )
}
