@file:Suppress("DEPRECATION")

package app.logdate.server.sync

import app.logdate.shared.model.sync.DeviceId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Exposed-based implementation of SyncRepository.
 * Note: user scoping (user_id) is not wired yet; add it alongside auth gating.
 */
class DbSyncRepository : SyncRepository {

    // --- Status ---
    override fun status(): SyncStatus = transaction {
        SyncStatus(
            contentCount = ContentSyncTable.selectAll().count().toInt(),
            journalCount = JournalSyncTable.selectAll().count().toInt(),
            associationCount = AssociationSyncTable.selectAll().count().toInt(),
            lastTimestamp = currentTimestamp()
        )
    }

    // --- Content ---
    override fun upsertContent(record: ContentRecord): ContentRecord = transaction {
        val existing = ContentSyncTable.select { ContentSyncTable.id eq record.id }.singleOrNull()
        val serverVersion = nextVersion(existing?.get(ContentSyncTable.serverVersion))
        if (existing == null) {
            ContentSyncTable.insert {
                it[id] = record.id
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
            ContentSyncTable.update({ ContentSyncTable.id eq record.id }) {
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

    override fun getContent(id: String): ContentRecord? = transaction {
        ContentSyncTable.select { ContentSyncTable.id eq id }
            .singleOrNull()
            ?.toContentRecord()
    }

    override fun deleteContent(id: String, deletedAt: Long) {
        transaction {
            val existingVersion = ContentSyncTable
                .select { ContentSyncTable.id eq id }
                .singleOrNull()
                ?.get(ContentSyncTable.serverVersion)
            val newVersion = nextVersion(existingVersion)
            ContentSyncTable.update({ ContentSyncTable.id eq id }) {
                it[deleted] = true
                it[ContentSyncTable.deletedAt] = deletedAt
                it[lastUpdated] = deletedAt
                it[ContentSyncTable.serverVersion] = newVersion
            }
        }
    }

    override fun contentChanges(since: Long): ChangeSet<ContentRecord, ContentDeletionMarker> = transaction {
        val changes = ContentSyncTable.select {
            (ContentSyncTable.lastUpdated greater since) or (ContentSyncTable.serverVersion greater since)
        }.mapNotNull { row ->
            if (row[ContentSyncTable.deleted]) null else row.toContentRecord()
        }
        val deletions = ContentSyncTable.select {
            (ContentSyncTable.deleted eq true) and (ContentSyncTable.deletedAt greater since)
        }.map { row ->
            ContentDeletionMarker(row[ContentSyncTable.id], row[ContentSyncTable.deletedAt] ?: currentTimestamp())
        }
        ChangeSet(changes, deletions, currentTimestamp())
    }

    // --- Journals ---
    override fun upsertJournal(record: JournalRecord): JournalRecord = transaction {
        val existing = JournalSyncTable.select { JournalSyncTable.id eq record.id }.singleOrNull()
        val serverVersion = nextVersion(existing?.get(JournalSyncTable.serverVersion))
        if (existing == null) {
            JournalSyncTable.insert {
                it[id] = record.id
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
            JournalSyncTable.update({ JournalSyncTable.id eq record.id }) {
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

    override fun getJournal(id: String): JournalRecord? = transaction {
        JournalSyncTable.select { JournalSyncTable.id eq id }
            .singleOrNull()
            ?.toJournalRecord()
    }

    override fun deleteJournal(id: String, deletedAt: Long) {
        transaction {
            val existingVersion = JournalSyncTable
                .select { JournalSyncTable.id eq id }
                .singleOrNull()
                ?.get(JournalSyncTable.serverVersion)
            val newVersion = nextVersion(existingVersion)
            JournalSyncTable.update({ JournalSyncTable.id eq id }) {
                it[deleted] = true
                it[JournalSyncTable.deletedAt] = deletedAt
                it[lastUpdated] = deletedAt
                it[JournalSyncTable.serverVersion] = newVersion
            }
        }
    }

    override fun journalChanges(since: Long): ChangeSet<JournalRecord, JournalDeletionMarker> = transaction {
        val changes = JournalSyncTable.select {
            (JournalSyncTable.lastUpdated greater since) or (JournalSyncTable.serverVersion greater since)
        }.mapNotNull { row ->
            if (row[JournalSyncTable.deleted]) null else row.toJournalRecord()
        }
        val deletions = JournalSyncTable.select {
            (JournalSyncTable.deleted eq true) and (JournalSyncTable.deletedAt greater since)
        }.map { row ->
            JournalDeletionMarker(row[JournalSyncTable.id], row[JournalSyncTable.deletedAt] ?: currentTimestamp())
        }
        ChangeSet(changes, deletions, currentTimestamp())
    }

    // --- Associations ---
    override fun upsertAssociations(records: List<AssociationRecord>) {
        transaction {
            records.forEach { record ->
                val key = AssociationKey(record.journalId, record.contentId)
                val existing = AssociationSyncTable.select {
                    (AssociationSyncTable.journalId eq key.journalId) and (AssociationSyncTable.contentId eq key.contentId)
                }.singleOrNull()
                val serverVersion = nextVersion(existing?.get(AssociationSyncTable.serverVersion))
                if (existing == null) {
                    AssociationSyncTable.insert {
                        it[journalId] = key.journalId
                        it[contentId] = key.contentId
                        it[createdAt] = record.createdAt
                        it[AssociationSyncTable.serverVersion] = serverVersion
                        it[deviceId] = record.deviceId.value
                        it[deleted] = false
                        it[deletedAt] = null
                    }
                } else {
                    AssociationSyncTable.update({
                        (AssociationSyncTable.journalId eq key.journalId) and (AssociationSyncTable.contentId eq key.contentId)
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

    override fun deleteAssociations(keys: List<AssociationKey>, deletedAt: Long) {
        transaction {
            keys.forEach { key ->
                val existingVersion = AssociationSyncTable
                    .select {
                        (AssociationSyncTable.journalId eq key.journalId) and (AssociationSyncTable.contentId eq key.contentId)
                    }
                    .singleOrNull()
                    ?.get(AssociationSyncTable.serverVersion)
                val newVersion = nextVersion(existingVersion)
                AssociationSyncTable.update({
                    (AssociationSyncTable.journalId eq key.journalId) and (AssociationSyncTable.contentId eq key.contentId)
                }) {
                    it[AssociationSyncTable.deleted] = true
                    it[AssociationSyncTable.deletedAt] = deletedAt
                    it[AssociationSyncTable.serverVersion] = newVersion
                }
            }
        }
    }

    override fun associationChanges(since: Long): ChangeSet<AssociationRecord, AssociationDeletionMarker> = transaction {
        val changes = AssociationSyncTable.select {
            (AssociationSyncTable.serverVersion greater since) or (AssociationSyncTable.createdAt greater since)
        }.mapNotNull { row ->
            if (row[AssociationSyncTable.deleted]) null else row.toAssociationRecord()
        }
        val deletions = AssociationSyncTable.select {
            (AssociationSyncTable.deleted eq true) and (AssociationSyncTable.deletedAt greater since)
        }.map { row ->
            AssociationDeletionMarker(
                AssociationKey(row[AssociationSyncTable.journalId], row[AssociationSyncTable.contentId]),
                row[AssociationSyncTable.deletedAt] ?: currentTimestamp()
            )
        }
        ChangeSet(changes, deletions, currentTimestamp())
    }

    // --- Media ---
    override fun upsertMedia(record: MediaRecord): MediaRecord = transaction {
        val id = if (record.mediaId.isBlank()) "media-${Random.nextLong().absoluteValue}" else record.mediaId
        val existing = MediaSyncTable.select { MediaSyncTable.mediaId eq id }.singleOrNull()
        val serverVersion = nextVersion(existing?.get(MediaSyncTable.serverVersion))
        if (existing == null) {
            MediaSyncTable.insert {
                it[mediaId] = id
                it[contentId] = record.contentId
                it[fileName] = record.fileName
                it[mimeType] = record.mimeType
                it[sizeBytes] = record.sizeBytes
                it[data] = record.data
                it[createdAt] = record.createdAt
                it[MediaSyncTable.serverVersion] = serverVersion
                it[deviceId] = record.deviceId.value
                it[deleted] = false
                it[deletedAt] = null
            }
        } else {
            MediaSyncTable.update({ MediaSyncTable.mediaId eq id }) {
                it[fileName] = record.fileName
                it[mimeType] = record.mimeType
                it[sizeBytes] = record.sizeBytes
                it[data] = record.data
                it[MediaSyncTable.serverVersion] = serverVersion
                it[deviceId] = record.deviceId.value
                it[deleted] = false
                it[deletedAt] = null
            }
        }
        record.copy(mediaId = id, serverVersion = serverVersion, createdAt = currentTimestamp())
    }

    override fun getMedia(mediaId: String): MediaRecord? = transaction {
        MediaSyncTable.select { MediaSyncTable.mediaId eq mediaId }
            .singleOrNull()
            ?.toMediaRecord()
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
