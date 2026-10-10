@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.server.sync

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi

/**
 * Exposed-based implementation of SyncRepository with user isolation.
 * All queries are scoped by user_id for multi-tenancy.
 */
class DbSyncRepository : SyncRepository {
    // --- Status ---
    override fun status(userId: UUID): SyncStatus =
        transaction {
            SyncStatus(
                contentCount =
                    ContentSyncTable
                        .selectAll()
                        .where { ContentSyncTable.userId eq userId }
                        .count()
                        .toInt(),
                journalCount =
                    JournalSyncTable
                        .selectAll()
                        .where { JournalSyncTable.userId eq userId }
                        .count()
                        .toInt(),
                associationCount =
                    AssociationSyncTable
                        .selectAll()
                        .where { AssociationSyncTable.userId eq userId }
                        .count()
                        .toInt(),
                lastTimestamp = currentTimestamp(),
            )
        }

    // --- Content ---
    override fun upsertContent(
        userId: UUID,
        record: ContentRecord,
    ): ContentRecord =
        transaction {
            val existing =
                ContentSyncTable
                    .selectAll()
                    .where {
                        (ContentSyncTable.id eq record.id) and (ContentSyncTable.userId eq userId)
                    }.singleOrNull()
            val serverVersion = nextVersion(existing?.get(ContentSyncTable.serverVersion))
            val acceptedTranscript =
                record.transcript ?: existing
                    ?.takeIf {
                        it[ContentSyncTable.type] == record.type &&
                            it[ContentSyncTable.mediaUri] == record.mediaUri &&
                            it[ContentSyncTable.durationMs] == record.durationMs
                    }?.get(ContentSyncTable.transcript)
            if (existing == null) {
                ContentSyncTable.insert {
                    it[id] = record.id
                    it[ContentSyncTable.userId] = userId
                    it[type] = record.type
                    it[content] = record.content
                    it[mediaUri] = record.mediaUri
                    it[transcript] = record.transcript
                    it[durationMs] = record.durationMs
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
                    it[transcript] = acceptedTranscript
                    it[durationMs] = record.durationMs
                    it[lastUpdated] = record.lastUpdated
                    it[ContentSyncTable.serverVersion] = serverVersion
                    it[deviceId] = record.deviceId.value
                    it[deleted] = false
                    it[deletedAt] = null
                }
            }
            record.copy(
                serverVersion = serverVersion,
                lastUpdated = currentTimestamp(),
                transcript = acceptedTranscript,
            )
        }

    override fun getContent(
        userId: UUID,
        id: String,
    ): ContentRecord? =
        transaction {
            ContentSyncTable
                .selectAll()
                .where {
                    (ContentSyncTable.id eq id) and
                        (ContentSyncTable.userId eq userId) and
                        (ContentSyncTable.deleted eq false)
                }.singleOrNull()
                ?.toContentRecord()
        }

    override fun deleteContent(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) {
        transaction {
            val existingVersion =
                ContentSyncTable
                    .selectAll()
                    .where { (ContentSyncTable.id eq id) and (ContentSyncTable.userId eq userId) }
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

    override fun contentChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): ChangeSet<ContentRecord, ContentDeletionMarker> =
        transaction {
            val changeRows =
                ContentSyncTable
                    .selectAll()
                    .where {
                        (ContentSyncTable.userId eq userId) and
                            (ContentSyncTable.deleted eq false) and
                            (ContentSyncTable.serverVersion greater since)
                    }.orderBy(ContentSyncTable.serverVersion to SortOrder.ASC)
                    .limit(limit + 1)
                    .toList()
            val hasMoreChanges = changeRows.size > limit
            val changes = changeRows.take(limit).map { it.toContentRecord() }
            val changeVersionMax = changes.maxOfOrNull { it.serverVersion }

            val deletionRows =
                ContentSyncTable
                    .selectAll()
                    .where {
                        (ContentSyncTable.userId eq userId) and
                            (ContentSyncTable.deleted eq true) and
                            (ContentSyncTable.serverVersion greater since)
                    }.orderBy(ContentSyncTable.serverVersion to SortOrder.ASC)
                    .limit(limit + 1)
                    .toList()
            val hasMoreDeletions = deletionRows.size > limit
            val deletions =
                deletionRows.take(limit).map { row ->
                    ContentDeletionMarker(
                        row[ContentSyncTable.id],
                        row[ContentSyncTable.deletedAt] ?: row[ContentSyncTable.lastUpdated],
                        row[ContentSyncTable.serverVersion],
                    )
                }
            val deletionVersionMax =
                deletionRows.take(limit).maxOfOrNull { it[ContentSyncTable.serverVersion] }

            val lastTimestamp =
                listOfNotNull(changeVersionMax, deletionVersionMax).maxOrNull() ?: since

            ChangeSet(changes, deletions, lastTimestamp, hasMoreChanges || hasMoreDeletions)
        }

    // --- Journals ---
    override fun upsertJournal(
        userId: UUID,
        record: JournalRecord,
    ): JournalRecord =
        transaction {
            val existing =
                JournalSyncTable
                    .selectAll()
                    .where {
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

    override fun getJournal(
        userId: UUID,
        id: String,
    ): JournalRecord? =
        transaction {
            JournalSyncTable
                .selectAll()
                .where {
                    (JournalSyncTable.id eq id) and
                        (JournalSyncTable.userId eq userId) and
                        (JournalSyncTable.deleted eq false)
                }.singleOrNull()
                ?.toJournalRecord()
        }

    override fun deleteJournal(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) {
        transaction {
            val existingVersion =
                JournalSyncTable
                    .selectAll()
                    .where { (JournalSyncTable.id eq id) and (JournalSyncTable.userId eq userId) }
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

    override fun journalChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): ChangeSet<JournalRecord, JournalDeletionMarker> =
        transaction {
            val changeRows =
                JournalSyncTable
                    .selectAll()
                    .where {
                        (JournalSyncTable.userId eq userId) and
                            (JournalSyncTable.deleted eq false) and
                            (JournalSyncTable.serverVersion greater since)
                    }.orderBy(JournalSyncTable.serverVersion to SortOrder.ASC)
                    .limit(limit + 1)
                    .toList()
            val hasMoreChanges = changeRows.size > limit
            val changes = changeRows.take(limit).map { it.toJournalRecord() }
            val changeVersionMax = changes.maxOfOrNull { it.serverVersion }

            val deletionRows =
                JournalSyncTable
                    .selectAll()
                    .where {
                        (JournalSyncTable.userId eq userId) and
                            (JournalSyncTable.deleted eq true) and
                            (JournalSyncTable.serverVersion greater since)
                    }.orderBy(JournalSyncTable.serverVersion to SortOrder.ASC)
                    .limit(limit + 1)
                    .toList()
            val hasMoreDeletions = deletionRows.size > limit
            val deletions =
                deletionRows.take(limit).map { row ->
                    JournalDeletionMarker(
                        row[JournalSyncTable.id],
                        row[JournalSyncTable.deletedAt] ?: row[JournalSyncTable.lastUpdated],
                        row[JournalSyncTable.serverVersion],
                    )
                }
            val deletionVersionMax =
                deletionRows.take(limit).maxOfOrNull { it[JournalSyncTable.serverVersion] }

            val lastTimestamp =
                listOfNotNull(changeVersionMax, deletionVersionMax).maxOrNull() ?: since

            ChangeSet(changes, deletions, lastTimestamp, hasMoreChanges || hasMoreDeletions)
        }

    // --- Associations ---
    override fun upsertAssociations(
        userId: UUID,
        records: List<AssociationRecord>,
    ) = dbUpsertAssociations(userId, records)

    override fun deleteAssociations(
        userId: UUID,
        keys: List<AssociationKey>,
        deletedAt: Long,
    ) = dbDeleteAssociations(userId, keys, deletedAt)

    override fun associationChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): ChangeSet<AssociationRecord, AssociationDeletionMarker> = dbAssociationChanges(userId, since, limit)

    override fun upsertMedia(
        userId: UUID,
        record: MediaRecord,
    ): MediaRecord = dbUpsertMedia(userId, record)

    override fun getMedia(
        userId: UUID,
        mediaId: String,
    ): MediaRecord? = dbGetMedia(userId, mediaId)

    override fun deleteMedia(
        userId: UUID,
        mediaId: String,
        deletedAt: Long,
    ) = dbDeleteMedia(userId, mediaId, deletedAt)

    override fun listAllMediaForUser(userId: UUID): List<MediaRecord> = dbListAllMediaForUser(userId)

    override fun createBackupRecord(
        userId: UUID,
        record: BackupRecord,
    ): BackupRecord = dbCreateBackupRecord(userId, record)

    override fun getBackupRecord(
        userId: UUID,
        id: UUID,
    ): BackupRecord? = dbGetBackupRecord(userId, id)

    override fun listBackups(userId: UUID): List<BackupRecord> = dbListBackups(userId)

    override fun deleteBackup(
        userId: UUID,
        id: UUID,
    ) = dbDeleteBackup(userId, id)

    override fun purgeTombstones(
        userId: UUID,
        olderThan: Long,
    ): SyncPurgeResult =
        transaction {
            val contentPurged =
                ContentSyncTable.deleteWhere {
                    (ContentSyncTable.userId eq userId) and
                        (deleted eq true) and
                        (deletedAt less olderThan)
                }
            val journalPurged =
                JournalSyncTable.deleteWhere {
                    (JournalSyncTable.userId eq userId) and
                        (JournalSyncTable.deleted eq true) and
                        (JournalSyncTable.deletedAt less olderThan)
                }
            val associationPurged =
                AssociationSyncTable.deleteWhere {
                    (AssociationSyncTable.userId eq userId) and
                        (AssociationSyncTable.deleted eq true) and
                        (AssociationSyncTable.deletedAt less olderThan)
                }
            val mediaPurged =
                MediaSyncTable.deleteWhere {
                    (MediaSyncTable.userId eq userId) and
                        (MediaSyncTable.deleted eq true) and
                        (MediaSyncTable.deletedAt less olderThan)
                }

            SyncPurgeResult(
                contentPurged = contentPurged,
                journalPurged = journalPurged,
                associationPurged = associationPurged,
                mediaPurged = mediaPurged,
                cutoff = olderThan,
            )
        }

    override fun purgeTombstonesOlderThan(olderThan: Long): SyncPurgeResult =
        transaction {
            val contentPurged =
                ContentSyncTable.deleteWhere {
                    (deleted eq true) and
                        (deletedAt less olderThan)
                }
            val journalPurged =
                JournalSyncTable.deleteWhere {
                    (deleted eq true) and
                        (deletedAt less olderThan)
                }
            val associationPurged =
                AssociationSyncTable.deleteWhere {
                    (deleted eq true) and
                        (deletedAt less olderThan)
                }
            val mediaPurged =
                MediaSyncTable.deleteWhere {
                    (deleted eq true) and
                        (deletedAt less olderThan)
                }

            SyncPurgeResult(
                contentPurged = contentPurged,
                journalPurged = journalPurged,
                associationPurged = associationPurged,
                mediaPurged = mediaPurged,
                cutoff = olderThan,
            )
        }

    // --- Helpers ---
}
