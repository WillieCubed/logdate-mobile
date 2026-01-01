package app.logdate.server.sync

import org.jetbrains.exposed.sql.Table

object ContentSyncTable : Table("sync_content") {
    val id = varchar("id", 128)
    val userId = uuid("user_id").nullable()
    val type = varchar("type", 32)
    val content = text("content").nullable()
    val mediaUri = text("media_uri").nullable()
    val createdAt = long("created_at")
    val lastUpdated = long("last_updated")
    val serverVersion = long("server_version")
    val deviceId = varchar("device_id", 128)
    val deleted = bool("deleted").default(false)
    val deletedAt = long("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object JournalSyncTable : Table("sync_journals") {
    val id = varchar("id", 128)
    val userId = uuid("user_id").nullable()
    val title = varchar("title", 256)
    val description = text("description")
    val createdAt = long("created_at")
    val lastUpdated = long("last_updated")
    val serverVersion = long("server_version")
    val deviceId = varchar("device_id", 128)
    val deleted = bool("deleted").default(false)
    val deletedAt = long("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AssociationSyncTable : Table("sync_associations") {
    val journalId = varchar("journal_id", 128)
    val contentId = varchar("content_id", 128)
    val userId = uuid("user_id").nullable()
    val createdAt = long("created_at")
    val serverVersion = long("server_version")
    val deviceId = varchar("device_id", 128)
    val deleted = bool("deleted").default(false)
    val deletedAt = long("deleted_at").nullable()

    override val primaryKey = PrimaryKey(journalId, contentId)
}

object MediaSyncTable : Table("sync_media") {
    val mediaId = varchar("media_id", 128)
    val contentId = varchar("content_id", 128)
    val userId = uuid("user_id").nullable()
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

    override val primaryKey = PrimaryKey(mediaId)
}
