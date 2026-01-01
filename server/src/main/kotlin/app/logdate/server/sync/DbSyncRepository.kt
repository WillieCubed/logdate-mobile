package app.logdate.server.sync

import app.logdate.shared.model.sync.DeviceId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Exposed-based implementation of SyncRepository with user isolation.
 * All queries are scoped by user_id for multi-tenancy.
 */
class DbSyncRepository : SyncRepository {

    // --- Status ---
    override fun status(userId: UUID): SyncStatus = transaction {
        SyncStatus(
            contentCount = ContentSyncTable.selectAll().where { ContentSyncTable.userId eq userId }.count().toInt(),
            journalCount = JournalSyncTable.selectAll().where { JournalSyncTable.userId eq userId }.count().toInt(),
            associationCount = AssociationSyncTable.selectAll().where { AssociationSyncTable.userId eq userId }.count().toInt(),
            lastTimestamp = currentTimestamp()
        )
    }

    // --- Content ---
    override fun upsertContent(userId: UUID, record: ContentRecord): ContentRecord = transaction {
        val existing = ContentSyncTable.selectAll().where {
            (ContentSyncTable.id eq record.id) and (ContentSyncTable.userId eq userId)
        }.singleOrNull()
        val serverVersion = nextVersion(existing?.get(ContentSyncTable.serverVersion))
        if (existing == null) {
            ContentSyncTable.insert {
                it[id] = record.id
                it[ContentSyncTable.userId] = userId
                it[type] = record.type
                it[content] = record.content
                it[mediaUri] = record.mediaUri
                it[createdAt] = record.createdAt
                it[lastUpdated] = record.lastUpdated
                it[ContentSyncTable.serverVersion] = serverVersion
                it[deviceId] = record.deviceId.value
                it[deleted] = false
                it[deletedAt] = null
            }
        } else {
            ContentSyncTable.update({ (ContentSyncTable.id eq record.id) and (ContentSyncTable.userId eq userId) }) {
                it[type] = record.type
                it[content] = record.content
                it[mediaUri] = record.mediaUri
                it[lastUpdated] = record.lastUpdated
                it[ContentSyncTable.serverVersion] = serverVersion
                it[deviceId] = record.deviceId.value
                it[deleted] = false
                it[deletedAt] = null
            }
        }
        record.copy(serverVersion = serverVersion, lastUpdated = currentTimestamp())
    }

    override fun getContent(userId: UUID, id: String): ContentRecord? = transaction {
        ContentSyncTable.selectAll().where {
            (ContentSyncTable.id eq id) and (ContentSyncTable.userId eq userId)
        }.singleOrNull()?.toContentRecord()
    }

    override fun deleteContent(userId: UUID, id: String, deletedAt: Long) {
        transaction {
            val existingVersion = ContentSyncTable
                .selectAll().where { (ContentSyncTable.id eq id) and (ContentSyncTable.userId eq userId) }
                .singleOrNull()
                ?.get(ContentSyncTable.serverVersion)
            val newVersion = nextVersion(existingVersion)
            ContentSyncTable.update({ (ContentSyncTable.id eq id) and (ContentSyncTable.userId eq userId) }) {
                it[deleted] = true
                it[ContentSyncTable.deletedAt] = deletedAt
                it[lastUpdated] = deletedAt
                it[ContentSyncTable.serverVersion] = newVersion
            }
        }
    }

    override fun contentChanges(userId: UUID, since: Long): ChangeSet<ContentRecord, ContentDeletionMarker> = transaction {
        val changes = ContentSyncTable.selectAll().where {
            (ContentSyncTable.userId eq userId) and
            ((ContentSyncTable.lastUpdated greater since) or (ContentSyncTable.serverVersion greater since))
        }.mapNotNull { row ->
            if (row[ContentSyncTable.deleted]) null else row.toContentRecord()
        }
        val deletions = ContentSyncTable.selectAll().where {
            (ContentSyncTable.userId eq userId) and
            (ContentSyncTable.deleted eq true) and
            (ContentSyncTable.deletedAt greater since)
        }.map { row ->
            ContentDeletionMarker(row[ContentSyncTable.id], row[ContentSyncTable.deletedAt] ?: currentTimestamp())
        }
        ChangeSet(changes, deletions, currentTimestamp())
    }

    // --- Journals ---
    override fun upsertJournal(userId: UUID, record: JournalRecord): JournalRecord = transaction {
        val existing = JournalSyncTable.selectAll().where {
            (JournalSyncTable.id eq record.id) and (JournalSyncTable.userId eq userId)
        }.singleOrNull()
        val serverVersion = nextVersion(existing?.get(JournalSyncTable.serverVersion))
        if (existing == null) {
            JournalSyncTable.insert {
                it[id] = record.id
                it[JournalSyncTable.userId] = userId
                it[title] = record.title
                it[description] = record.description
                it[createdAt] = record.createdAt
                it[lastUpdated] = record.lastUpdated
                it[JournalSyncTable.serverVersion] = serverVersion
                it[deviceId] = record.deviceId.value
                it[deleted] = false
                it[deletedAt] = null
            }
        } else {
            JournalSyncTable.update({ (JournalSyncTable.id eq record.id) and (JournalSyncTable.userId eq userId) }) {
                it[title] = record.title
                it[description] = record.description
                it[lastUpdated] = record.lastUpdated
                it[JournalSyncTable.serverVersion] = serverVersion
                it[deviceId] = record.deviceId.value
                it[deleted] = false
                it[deletedAt] = null
            }
        }
        record.copy(serverVersion = serverVersion, lastUpdated = currentTimestamp())
    }

    override fun getJournal(userId: UUID, id: String): JournalRecord? = transaction {
        JournalSyncTable.selectAll().where {
            (JournalSyncTable.id eq id) and (JournalSyncTable.userId eq userId)
        }.singleOrNull()?.toJournalRecord()
    }

    override fun deleteJournal(userId: UUID, id: String, deletedAt: Long) {
        transaction {
            val existingVersion = JournalSyncTable
                .selectAll().where { (JournalSyncTable.id eq id) and (JournalSyncTable.userId eq userId) }
                .singleOrNull()
                ?.get(JournalSyncTable.serverVersion)
            val newVersion = nextVersion(existingVersion)
            JournalSyncTable.update({ (JournalSyncTable.id eq id) and (JournalSyncTable.userId eq userId) }) {
                it[deleted] = true
                it[JournalSyncTable.deletedAt] = deletedAt
                it[lastUpdated] = deletedAt
                it[JournalSyncTable.serverVersion] = newVersion
            }
        }
    }

    override fun journalChanges(userId: UUID, since: Long): ChangeSet<JournalRecord, JournalDeletionMarker> = transaction {
        val changes = JournalSyncTable.selectAll().where {
            (JournalSyncTable.userId eq userId) and
            ((JournalSyncTable.lastUpdated greater since) or (JournalSyncTable.serverVersion greater since))
        }.mapNotNull { row ->
            if (row[JournalSyncTable.deleted]) null else row.toJournalRecord()
        }
        val deletions = JournalSyncTable.selectAll().where {
            (JournalSyncTable.userId eq userId) and
            (JournalSyncTable.deleted eq true) and
            (JournalSyncTable.deletedAt greater since)
        }.map { row ->
            JournalDeletionMarker(row[JournalSyncTable.id], row[JournalSyncTable.deletedAt] ?: currentTimestamp())
        }
        ChangeSet(changes, deletions, currentTimestamp())
    }

    // --- Associations ---
    override fun upsertAssociations(userId: UUID, records: List<AssociationRecord>) {
        transaction {
            records.forEach { record ->
                val key = AssociationKey(record.journalId, record.contentId)
                val existing = AssociationSyncTable.selectAll().where {
                    (AssociationSyncTable.journalId eq key.journalId) and
                    (AssociationSyncTable.contentId eq key.contentId) and
                    (AssociationSyncTable.userId eq userId)
                }.singleOrNull()
                val serverVersion = nextVersion(existing?.get(AssociationSyncTable.serverVersion))
                if (existing == null) {
                    AssociationSyncTable.insert {
                        it[journalId] = key.journalId
                        it[contentId] = key.contentId
                        it[AssociationSyncTable.userId] = userId
                        it[createdAt] = record.createdAt
                        it[AssociationSyncTable.serverVersion] = serverVersion
                        it[deviceId] = record.deviceId.value
                        it[deleted] = false
                        it[deletedAt] = null
                    }
                } else {
                    AssociationSyncTable.update({
                        (AssociationSyncTable.journalId eq key.journalId) and
                        (AssociationSyncTable.contentId eq key.contentId) and
                        (AssociationSyncTable.userId eq userId)
                    }) {
                        it[createdAt] = record.createdAt
                        it[AssociationSyncTable.serverVersion] = serverVersion
                        it[deviceId] = record.deviceId.value
                        it[deleted] = false
                        it[deletedAt] = null
                    }
                }
            }
        }
    }

    override fun deleteAssociations(userId: UUID, keys: List<AssociationKey>, deletedAt: Long) {
        transaction {
            keys.forEach { key ->
                val existingVersion = AssociationSyncTable
                    .selectAll().where {
                        (AssociationSyncTable.journalId eq key.journalId) and
                        (AssociationSyncTable.contentId eq key.contentId) and
                        (AssociationSyncTable.userId eq userId)
                    }
                    .singleOrNull()
                    ?.get(AssociationSyncTable.serverVersion)
                val newVersion = nextVersion(existingVersion)
                AssociationSyncTable.update({
                    (AssociationSyncTable.journalId eq key.journalId) and
                    (AssociationSyncTable.contentId eq key.contentId) and
                    (AssociationSyncTable.userId eq userId)
                }) {
                    it[AssociationSyncTable.deleted] = true
                    it[AssociationSyncTable.deletedAt] = deletedAt
                    it[AssociationSyncTable.serverVersion] = newVersion
                }
            }
        }
    }

    override fun associationChanges(userId: UUID, since: Long): ChangeSet<AssociationRecord, AssociationDeletionMarker> = transaction {
        val changes = AssociationSyncTable.selectAll().where {
            (AssociationSyncTable.userId eq userId) and
            ((AssociationSyncTable.serverVersion greater since) or (AssociationSyncTable.createdAt greater since))
        }.mapNotNull { row ->
            if (row[AssociationSyncTable.deleted]) null else row.toAssociationRecord()
        }
        val deletions = AssociationSyncTable.selectAll().where {
            (AssociationSyncTable.userId eq userId) and
            (AssociationSyncTable.deleted eq true) and
            (AssociationSyncTable.deletedAt greater since)
        }.map { row ->
            AssociationDeletionMarker(
                AssociationKey(row[AssociationSyncTable.journalId], row[AssociationSyncTable.contentId]),
                row[AssociationSyncTable.deletedAt] ?: currentTimestamp()
            )
        }
        ChangeSet(changes, deletions, currentTimestamp())
    }

    // --- Media ---
    override fun upsertMedia(userId: UUID, record: MediaRecord): MediaRecord = transaction {
        val id = if (record.mediaId.isBlank()) "media-${Random.nextLong().absoluteValue}" else record.mediaId
        val existing = MediaSyncTable.selectAll().where {
            (MediaSyncTable.mediaId eq id) and (MediaSyncTable.userId eq userId)
        }.singleOrNull()
        val serverVersion = nextVersion(existing?.get(MediaSyncTable.serverVersion))
        if (existing == null) {
            MediaSyncTable.insert {
                it[mediaId] = id
                it[MediaSyncTable.userId] = userId
                it[contentId] = record.contentId
                it[fileName] = record.fileName
                it[mimeType] = record.mimeType
                it[sizeBytes] = record.sizeBytes
                it[data] = record.data
                it[storagePath] = record.storagePath
                it[createdAt] = record.createdAt
                it[MediaSyncTable.serverVersion] = serverVersion
                it[deviceId] = record.deviceId.value
                it[deleted] = false
                it[deletedAt] = null
            }
        } else {
            MediaSyncTable.update({ (MediaSyncTable.mediaId eq id) and (MediaSyncTable.userId eq userId) }) {
                it[fileName] = record.fileName
                it[mimeType] = record.mimeType
                it[sizeBytes] = record.sizeBytes
                it[data] = record.data
                it[storagePath] = record.storagePath
                it[MediaSyncTable.serverVersion] = serverVersion
                it[deviceId] = record.deviceId.value
                it[deleted] = false
                it[deletedAt] = null
            }
        }
        record.copy(mediaId = id, serverVersion = serverVersion, createdAt = currentTimestamp())
    }

    override fun getMedia(userId: UUID, mediaId: String): MediaRecord? = transaction {
        MediaSyncTable.selectAll().where {
            (MediaSyncTable.mediaId eq mediaId) and (MediaSyncTable.userId eq userId)
        }.singleOrNull()?.toMediaRecord()
    }

    // --- Helpers ---
    private fun ResultRow.toContentRecord(): ContentRecord = ContentRecord(
        id = this[ContentSyncTable.id],
        type = this[ContentSyncTable.type],
        content = this[ContentSyncTable.content],
        mediaUri = this[ContentSyncTable.mediaUri],
        createdAt = this[ContentSyncTable.createdAt],
        lastUpdated = this[ContentSyncTable.lastUpdated],
        serverVersion = this[ContentSyncTable.serverVersion],
        deviceId = DeviceId(this[ContentSyncTable.deviceId])
    )

    private fun ResultRow.toJournalRecord(): JournalRecord = JournalRecord(
        id = this[JournalSyncTable.id],
        title = this[JournalSyncTable.title],
        description = this[JournalSyncTable.description],
        createdAt = this[JournalSyncTable.createdAt],
        lastUpdated = this[JournalSyncTable.lastUpdated],
        serverVersion = this[JournalSyncTable.serverVersion],
        deviceId = DeviceId(this[JournalSyncTable.deviceId])
    )

    private fun ResultRow.toAssociationRecord(): AssociationRecord = AssociationRecord(
        journalId = this[AssociationSyncTable.journalId],
        contentId = this[AssociationSyncTable.contentId],
        createdAt = this[AssociationSyncTable.createdAt],
        serverVersion = this[AssociationSyncTable.serverVersion],
        deviceId = DeviceId(this[AssociationSyncTable.deviceId])
    )

    private fun ResultRow.toMediaRecord(): MediaRecord = MediaRecord(
        mediaId = this[MediaSyncTable.mediaId],
        contentId = this[MediaSyncTable.contentId],
        fileName = this[MediaSyncTable.fileName],
        mimeType = this[MediaSyncTable.mimeType],
        sizeBytes = this[MediaSyncTable.sizeBytes],
        data = this[MediaSyncTable.data],
        storagePath = this[MediaSyncTable.storagePath],
        createdAt = this[MediaSyncTable.createdAt],
        serverVersion = this[MediaSyncTable.serverVersion],
        deviceId = DeviceId(this[MediaSyncTable.deviceId])
    )

    private fun nextVersion(existingVersion: Long?): Long {
        val candidate = currentTimestamp()
        return if (existingVersion != null && candidate <= existingVersion) existingVersion + 1 else candidate
    }

    private fun currentTimestamp(): Long = System.currentTimeMillis()
}
