package app.logdate.server.sync

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Table.PrimaryKey

/**
 * Stores synced content metadata. Body remains nullable for cases where blobs are external.
 */
object ContentSyncTable : Table("sync_content") {
    val id = varchar("id", 128)
    val userId = uuid("user_id")
    val type = varchar("type", 32)
    val content = text("content").nullable()
    val mediaUri = text("media_uri").nullable()
    val durationMs = long("duration_ms").nullable()
    val createdAt = long("created_at")
    val lastUpdated = long("last_updated")
    val serverVersion = long("server_version")
    val deviceId = varchar("device_id", 128)
    val deleted = bool("deleted").default(false)
    val deletedAt = long("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_sync_content_user", false, userId)
        index("idx_sync_content_user_deleted_version", false, userId, deleted, serverVersion)
    }
}

/**
 * Stores synced journal metadata. Title is text-type to support encrypted Base64.
 */
object JournalSyncTable : Table("sync_journals") {
    val id = varchar("id", 128)
    val userId = uuid("user_id")
    val title = text("title")
    val description = text("description")
    val createdAt = long("created_at")
    val lastUpdated = long("last_updated")
    val serverVersion = long("server_version")
    val deviceId = varchar("device_id", 128)
    val deleted = bool("deleted").default(false)
    val deletedAt = long("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_sync_journal_user", false, userId)
        index("idx_sync_journal_user_deleted_version", false, userId, deleted, serverVersion)
    }
}

/**
 * Many-to-many relationship tracking for content items within journals.
 */
object AssociationSyncTable : Table("sync_associations") {
    val journalId = varchar("journal_id", 128)
    val contentId = varchar("content_id", 128)
    val userId = uuid("user_id")
    val createdAt = long("created_at")
    val serverVersion = long("server_version")
    val deviceId = varchar("device_id", 128)
    val deleted = bool("deleted").default(false)
    val deletedAt = long("deleted_at").nullable()

    override val primaryKey = PrimaryKey(journalId, contentId)

    init {
        index("idx_sync_association_user", false, userId)
        index("idx_sync_association_user_deleted_version", false, userId, deleted, serverVersion)
    }
}

/**
 * Tracks media blob metadata. Actual file data is either in 'data' (DB) or 'storage_path' (GCS).
 */
object MediaSyncTable : Table("sync_media") {
    val mediaId = varchar("media_id", 128)
    val contentId = varchar("content_id", 128)
    val userId = uuid("user_id")
    val fileName = varchar("file_name", 256)
    val mimeType = varchar("mime_type", 128)
    val sizeBytes = long("size_bytes")
    val data = binary("data")
    val storagePath = text("storage_path").nullable()
    val createdAt = long("created_at")
    val serverVersion = long("server_version")
    val deviceId = varchar("device_id", 128)
    val deleted = bool("deleted").default(false)
    val deletedAt = long("deleted_at").nullable()
    val encryptionVersion = integer("encryption_version").nullable()
    val encryptionKeyId = varchar("encryption_key_id", 128).nullable()
    val encryptionMode = varchar("encryption_mode", 16).nullable()

    override val primaryKey = PrimaryKey(mediaId)

    init {
        index("idx_sync_media_user", false, userId)
        index("idx_sync_media_user_content", false, userId, contentId)
    }
}

/**
 * Registry for sovereign encrypted backups stored by the user.
 */
object BackupSyncTable : Table("sync_backups") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val deviceId = varchar("device_id", 128)
    val manifest = text("manifest")
    val storagePath = text("storage_path")
    val createdAt = long("created_at")
    val sizeBytes = long("size_bytes")
    val encryptionVersion = integer("encryption_version").nullable()
    val encryptionKeyId = varchar("encryption_key_id", 128).nullable()
    val encryptionMode = varchar("encryption_mode", 16).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_sync_backup_user", false, userId)
    }
}
