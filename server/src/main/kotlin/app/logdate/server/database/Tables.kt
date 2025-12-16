package app.logdate.server.database

import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlin.uuid.ExperimentalUuidApi
import java.util.UUID

/**
 * Database table definitions using Jetbrains Exposed ORM.
 * Using regular Table with explicit UUID columns for better type control.
 */

@OptIn(ExperimentalUuidApi::class)
object AccountsTable : Table("accounts") {
    val id = uuid("id").autoGenerate()
    val username = varchar("username", 50).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val email = varchar("email", 255).nullable()
    val bio = text("bio").nullable()
    val createdAt = timestamp("created_at")
    val lastSignInAt = timestamp("last_sign_in_at").nullable()
    val isActive = bool("is_active").default(true)
    val preferences = text("preferences").default("{}")
    
    override val primaryKey = PrimaryKey(id)
}

@OptIn(ExperimentalUuidApi::class)
object PasskeysTable : Table("passkeys") {
    val id = uuid("id").autoGenerate()
    val accountId = uuid("account_id").references(AccountsTable.id)
    val credentialId = text("credential_id").uniqueIndex()
    val publicKey = text("public_key")
    val signCount = long("sign_count").default(0)
    val nickname = varchar("nickname", 100).nullable()
    val deviceType = varchar("device_type", 50).default("platform")
    val createdAt = timestamp("created_at")
    val lastUsedAt = timestamp("last_used_at").nullable()
    val isActive = bool("is_active").default(true)
    val webauthnData = text("webauthn_data").default("{}")
    
    override val primaryKey = PrimaryKey(id)
}

object SessionsTable : Table("sessions") {
    val id = varchar("id", 64)
    val temporaryUserId = uuid("temporary_user_id")
    val challenge = text("challenge")
    val sessionType = varchar("session_type", 20)
    val username = varchar("username", 50).nullable()
    val deviceInfo = text("device_info").nullable()
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val isUsed = bool("is_used").default(false)
    
    override val primaryKey = PrimaryKey(id)
}

object WebAuthnChallengesTable : Table("webauthn_challenges") {
    val challenge = text("challenge")
    val userId = uuid("user_id")
    val challengeType = varchar("challenge_type", 20)
    val expiresAt = timestamp("expires_at")
    val isUsed = bool("is_used").default(false)
    val createdAt = timestamp("created_at")
    
    override val primaryKey = PrimaryKey(challenge)
}

/**
 * Custom data types for JSON serialization
 */
data class UserPreferences(
    val theme: String? = null,
    val language: String? = null,
    val notifications: Map<String, Boolean> = emptyMap(),
    val privacy: Map<String, Any> = emptyMap()
)

data class WebAuthnData(
    val attestationType: String? = null,
    val transports: List<String> = emptyList(),
    val userVerification: String? = null,
    val extensions: Map<String, Any> = emptyMap()
)

