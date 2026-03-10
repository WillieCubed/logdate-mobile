package app.logdate.server.database

import app.logdate.server.logdate.LogDateCollectionChangesMetadata
import app.logdate.server.logdate.LogDateCollectionKind
import app.logdate.server.logdate.LogDateCollectionMetadata
import app.logdate.server.logdate.LogDateCollectionsMetadataStore
import app.logdate.server.logdate.LogDateCollectionsPurgeResult
import app.logdate.server.logdate.LogDateCollectionsState
import app.logdate.server.logdate.LogDateCollectionsStatus
import app.logdate.server.util.toKotlinxInstant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import studio.hypertext.atproto.identity.AtprotoDid
import java.util.UUID
import kotlin.time.Clock

internal class PostgreSQLLogDateCollectionsMetadataStore : LogDateCollectionsMetadataStore {
    override suspend fun status(userId: UUID): LogDateCollectionsStatus =
        transaction {
            val state =
                LogDateCollectionStatesTable
                    .selectAll()
                    .where { LogDateCollectionStatesTable.userId eq userId }
                    .singleOrNull()
                    ?.toCollectionsState()
            LogDateCollectionsStatus(
                entryCount = liveCount(userId, LogDateCollectionKind.ENTRY),
                journalCount = liveCount(userId, LogDateCollectionKind.JOURNAL),
                associationCount = liveCount(userId, LogDateCollectionKind.ASSOCIATION),
                lastTimestamp = state?.lastVersion ?: System.currentTimeMillis(),
            )
        }

    override suspend fun state(userId: UUID): LogDateCollectionsState? =
        transaction {
            LogDateCollectionStatesTable
                .selectAll()
                .where { LogDateCollectionStatesTable.userId eq userId }
                .singleOrNull()
                ?.toCollectionsState()
        }

    override suspend fun listLive(
        userId: UUID,
        collection: LogDateCollectionKind,
    ): List<LogDateCollectionMetadata> =
        transaction {
            LogDateCollectionRecordsTable
                .selectAll()
                .where {
                    (LogDateCollectionRecordsTable.userId eq userId) and
                        (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                        (LogDateCollectionRecordsTable.deleted eq false)
                }.orderBy(LogDateCollectionRecordsTable.serverVersion to SortOrder.ASC)
                .map(ResultRow::toCollectionMetadata)
        }

    override suspend fun metadata(
        userId: UUID,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata? =
        transaction {
            LogDateCollectionRecordsTable
                .selectAll()
                .where {
                    (LogDateCollectionRecordsTable.userId eq userId) and
                        (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                        (LogDateCollectionRecordsTable.recordKey eq recordKey) and
                        (LogDateCollectionRecordsTable.deleted eq false)
                }.singleOrNull()
                ?.toCollectionMetadata()
        }

    override suspend fun changes(
        userId: UUID,
        collection: LogDateCollectionKind,
        since: Long,
        limit: Int,
    ): LogDateCollectionChangesMetadata =
        transaction {
            val changeRows =
                LogDateCollectionRecordsTable
                    .selectAll()
                    .where {
                        (LogDateCollectionRecordsTable.userId eq userId) and
                            (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                            (LogDateCollectionRecordsTable.deleted eq false) and
                            (LogDateCollectionRecordsTable.serverVersion greater since)
                    }.orderBy(LogDateCollectionRecordsTable.serverVersion to SortOrder.ASC)
                    .limit(limit + 1)
                    .toList()
            val deletionRows =
                LogDateCollectionRecordsTable
                    .selectAll()
                    .where {
                        (LogDateCollectionRecordsTable.userId eq userId) and
                            (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                            (LogDateCollectionRecordsTable.deleted eq true) and
                            (LogDateCollectionRecordsTable.serverVersion greater since)
                    }.orderBy(LogDateCollectionRecordsTable.serverVersion to SortOrder.ASC)
                    .limit(limit + 1)
                    .toList()
            val limitedChanges = changeRows.take(limit)
            val limitedDeletions = deletionRows.take(limit)
            LogDateCollectionChangesMetadata(
                changes = limitedChanges.map(ResultRow::toCollectionMetadata),
                deletions = limitedDeletions.map(ResultRow::toCollectionMetadata),
                lastTimestamp =
                    listOfNotNull(
                        limitedChanges.maxOfOrNull { it[LogDateCollectionRecordsTable.serverVersion] },
                        limitedDeletions.maxOfOrNull { it[LogDateCollectionRecordsTable.serverVersion] },
                    ).maxOrNull() ?: since,
                hasMore = changeRows.size > limit || deletionRows.size > limit,
            )
        }

    override suspend fun upsert(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata =
        transaction {
            val state = nextState(userId, repoDid)
            val existing =
                LogDateCollectionRecordsTable
                    .selectAll()
                    .where {
                        (LogDateCollectionRecordsTable.userId eq userId) and
                            (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                            (LogDateCollectionRecordsTable.recordKey eq recordKey)
                    }.singleOrNull()
            if (existing == null) {
                LogDateCollectionRecordsTable.insert {
                    it[LogDateCollectionRecordsTable.userId] = userId
                    it[LogDateCollectionRecordsTable.collection] = collection.storageName
                    it[LogDateCollectionRecordsTable.recordKey] = recordKey
                    it[serverVersion] = state.lastVersion
                    it[deleted] = false
                    it[deletedAt] = null
                }
            } else {
                LogDateCollectionRecordsTable.update({
                    (LogDateCollectionRecordsTable.userId eq userId) and
                        (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                        (LogDateCollectionRecordsTable.recordKey eq recordKey)
                }) {
                    it[serverVersion] = state.lastVersion
                    it[deleted] = false
                    it[deletedAt] = null
                }
            }
            LogDateCollectionMetadata(recordKey = recordKey, version = state.lastVersion, deletedAt = null)
        }

    override suspend fun delete(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordKey: String,
        deletedAt: Long,
    ): LogDateCollectionMetadata? =
        transaction {
            val existing =
                LogDateCollectionRecordsTable
                    .selectAll()
                    .where {
                        (LogDateCollectionRecordsTable.userId eq userId) and
                            (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                            (LogDateCollectionRecordsTable.recordKey eq recordKey)
                    }.singleOrNull() ?: return@transaction null
            val state = nextState(userId, repoDid)
            LogDateCollectionRecordsTable.update({
                (LogDateCollectionRecordsTable.userId eq userId) and
                    (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                    (LogDateCollectionRecordsTable.recordKey eq recordKey)
            }) {
                it[serverVersion] = state.lastVersion
                it[LogDateCollectionRecordsTable.deleted] = true
                it[LogDateCollectionRecordsTable.deletedAt] = deletedAt
            }
            existing.toCollectionMetadata().copy(version = state.lastVersion, deletedAt = deletedAt)
        }

    override suspend fun purgeTombstones(
        userId: UUID,
        olderThan: Long,
    ): LogDateCollectionsPurgeResult =
        transaction {
            val rowsToRemove =
                LogDateCollectionRecordsTable
                    .selectAll()
                    .where {
                        (LogDateCollectionRecordsTable.userId eq userId) and
                            (LogDateCollectionRecordsTable.deleted eq true) and
                            (LogDateCollectionRecordsTable.deletedAt less olderThan)
                    }.toList()
            rowsToRemove.forEach { row ->
                LogDateCollectionRecordsTable.deleteWhere {
                    (LogDateCollectionRecordsTable.userId eq userId) and
                        (LogDateCollectionRecordsTable.collection eq row[LogDateCollectionRecordsTable.collection]) and
                        (LogDateCollectionRecordsTable.recordKey eq row[LogDateCollectionRecordsTable.recordKey])
                }
            }
            LogDateCollectionsPurgeResult(
                entryPurged =
                    rowsToRemove.count {
                        it[LogDateCollectionRecordsTable.collection] == LogDateCollectionKind.ENTRY.storageName
                    },
                journalPurged =
                    rowsToRemove.count {
                        it[LogDateCollectionRecordsTable.collection] == LogDateCollectionKind.JOURNAL.storageName
                    },
                associationPurged =
                    rowsToRemove.count {
                        it[LogDateCollectionRecordsTable.collection] == LogDateCollectionKind.ASSOCIATION.storageName
                    },
                cutoff = olderThan,
            )
        }

    private fun liveCount(
        userId: UUID,
        collection: LogDateCollectionKind,
    ): Int =
        LogDateCollectionRecordsTable
            .selectAll()
            .where {
                (LogDateCollectionRecordsTable.userId eq userId) and
                    (LogDateCollectionRecordsTable.collection eq collection.storageName) and
                    (LogDateCollectionRecordsTable.deleted eq false)
            }.count()
            .toInt()

    private fun nextState(
        userId: UUID,
        preferredRepoDid: AtprotoDid,
    ): LogDateCollectionsState {
        val existing =
            LogDateCollectionStatesTable
                .selectAll()
                .where { LogDateCollectionStatesTable.userId eq userId }
                .singleOrNull()
        val repoDid = existing?.get(LogDateCollectionStatesTable.repoDid) ?: preferredRepoDid.toString()
        val version = nextVersion(existing?.get(LogDateCollectionStatesTable.lastVersion))
        if (existing == null) {
            LogDateCollectionStatesTable.insert {
                it[LogDateCollectionStatesTable.userId] = userId
                it[LogDateCollectionStatesTable.repoDid] = repoDid
                it[lastVersion] = version
                it[updatedAt] = Clock.System.now().toKotlinxInstant()
            }
        } else {
            LogDateCollectionStatesTable.update({ LogDateCollectionStatesTable.userId eq userId }) {
                it[lastVersion] = version
                it[updatedAt] = Clock.System.now().toKotlinxInstant()
            }
        }
        return LogDateCollectionsState(
            repoDid = AtprotoDid.require(repoDid),
            lastVersion = version,
        )
    }

    private fun nextVersion(existingVersion: Long?): Long {
        val candidate = System.currentTimeMillis()
        return if (existingVersion != null && candidate <= existingVersion) existingVersion + 1L else candidate
    }
}

private fun ResultRow.toCollectionsState(): LogDateCollectionsState =
    LogDateCollectionsState(
        repoDid = AtprotoDid.require(this[LogDateCollectionStatesTable.repoDid]),
        lastVersion = this[LogDateCollectionStatesTable.lastVersion],
    )

private fun ResultRow.toCollectionMetadata(): LogDateCollectionMetadata =
    LogDateCollectionMetadata(
        recordKey = this[LogDateCollectionRecordsTable.recordKey],
        version = this[LogDateCollectionRecordsTable.serverVersion],
        deletedAt = this[LogDateCollectionRecordsTable.deletedAt],
    )
