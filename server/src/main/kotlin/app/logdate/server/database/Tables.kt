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
    val did = varchar("did", 255).nullable().uniqueIndex()
    val handle = varchar("handle", 255).nullable().uniqueIndex()
    val signingKeyPublic = text("signing_key_public").nullable()
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
object SigningKeysTable : Table("signing_keys") {
    val id = uuid("id").autoGenerate()
    val accountId = uuid("account_id").references(AccountsTable.id)
    val purpose = varchar("purpose", 32).default("atproto")
    val algorithm = varchar("algorithm", 32).default("P-256")
    val publicKeyMultibase = text("public_key_multibase")
    val privateKeyEncrypted = text("private_key_encrypted")
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()

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

object AtprotoRepoHeadsTable : Table("atproto_repo_heads") {
    val repoDid = varchar("repo_did", 255)
    val rootCid = varchar("root_cid", 255)
    val commitCid = varchar("commit_cid", 255)
    val revision = long("revision")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(repoDid)
}

object AtprotoRepoBlocksTable : Table("atproto_repo_blocks") {
    val cid = varchar("cid", 255)
    val bytes = binary("bytes")

    override val primaryKey = PrimaryKey(cid)
}

object AtprotoRepoBlockLinksTable : Table("atproto_repo_block_links") {
    val repoDid = varchar("repo_did", 255)
    val cid = varchar("cid", 255).references(AtprotoRepoBlocksTable.cid)

    override val primaryKey = PrimaryKey(repoDid, cid)

    init {
        index("idx_atproto_repo_block_links_repo", false, repoDid)
    }
}

object AtprotoRepoCommitsTable : Table("atproto_repo_commits") {
    val repoDid = varchar("repo_did", 255)
    val revision = long("revision")
    val cid = varchar("cid", 255)
    val rootCid = varchar("root_cid", 255)
    val prevCid = varchar("prev_cid", 255).nullable()
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val recordCount = integer("record_count")
    val signature = text("signature")

    override val primaryKey = PrimaryKey(repoDid, revision)

    init {
        index("idx_atproto_repo_commits_repo_revision", false, repoDid, revision)
    }
}

object LogDateCollectionStatesTable : Table("logdate_collection_states") {
    val userId = uuid("user_id")
    val repoDid = varchar("repo_did", 255)
    val lastVersion = long("last_version")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object LogDateCollectionRecordsTable : Table("logdate_collection_records") {
    val userId = uuid("user_id")
    val collection = varchar("collection", 32)
    val recordKey = varchar("record_key", 255)
    val serverVersion = long("server_version")
    val deleted = bool("deleted").default(false)
    val deletedAt = long("deleted_at").nullable()

    override val primaryKey = PrimaryKey(userId, collection, recordKey)

    init {
        index(
            "idx_logdate_collection_records_user_collection_deleted_version",
            false,
            userId,
            collection,
            deleted,
            serverVersion,
        )
    }
}
