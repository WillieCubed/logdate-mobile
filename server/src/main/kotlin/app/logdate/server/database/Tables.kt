package app.logdate.server.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlin.uuid.ExperimentalUuidApi

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
    val emailVerified = bool("email_verified").default(false)
    val bio = text("bio").nullable()
    val createdAt = timestamp("created_at")
    val lastSignInAt = timestamp("last_sign_in_at").nullable()
    val isActive = bool("is_active").default(true)
    val preferences = text("preferences").default("{}")

    override val primaryKey = PrimaryKey(id)
}

@OptIn(ExperimentalUuidApi::class)
object AccountIdentitiesTable : Table("account_identities") {
    val id = uuid("id").autoGenerate()
    val accountId = uuid("account_id").references(AccountsTable.id)
    val provider = varchar("provider", 32)
    val providerSubject = varchar("provider_subject", 255)
    val email = varchar("email", 255).nullable()
    val emailVerified = bool("email_verified").default(false)
    val createdAt = timestamp("created_at")
    val lastSignInAt = timestamp("last_sign_in_at").nullable()
    val metadataJson = text("metadata_json").default("{}")

    override val primaryKey = PrimaryKey(id)
}

@OptIn(ExperimentalUuidApi::class)
object AccountLinkEventsTable : Table("account_link_events") {
    val id = uuid("id").autoGenerate()
    val accountId = uuid("account_id").references(AccountsTable.id)
    val provider = varchar("provider", 32)
    val providerSubject = varchar("provider_subject", 255)
    val reason = varchar("reason", 64)
    val ipHash = varchar("ip_hash", 128).nullable()
    val userAgentHash = varchar("user_agent_hash", 128).nullable()
    val createdAt = timestamp("created_at")
    val metadataJson = text("metadata_json").default("{}")

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
    val displayName = varchar("display_name", 100).nullable()
    val bio = text("bio").nullable()
    val deviceInfo = text("device_info").nullable()
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val isUsed = bool("is_used").default(false)

    override val primaryKey = PrimaryKey(id)
}
