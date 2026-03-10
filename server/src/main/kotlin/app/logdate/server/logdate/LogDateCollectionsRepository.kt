package app.logdate.server.logdate

import app.logdate.shared.model.sync.DeviceId
import java.util.UUID

/**
 * LogDate-owned collection models used internally by the server.
 *
 * These stay protocol-agnostic so AT Protocol remains an integration boundary rather than the
 * language of the core application.
 */
data class LogDateEntry(
    val id: String,
    val type: String,
    val content: String?,
    val mediaUri: String?,
    val durationMs: Long?,
    val createdAt: Long,
    val lastUpdated: Long,
    val version: Long,
    val deviceId: DeviceId,
)

data class LogDateJournal(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val version: Long,
    val deviceId: DeviceId,
)

data class LogDateAssociation(
    val journalId: String,
    val entryId: String,
    val createdAt: Long,
    val version: Long,
    val deviceId: DeviceId,
)

data class LogDateCollectionsStatus(
    val entryCount: Int,
    val journalCount: Int,
    val associationCount: Int,
    val lastTimestamp: Long,
)

data class LogDateCollectionsPurgeResult(
    val entryPurged: Int,
    val journalPurged: Int,
    val associationPurged: Int,
    val cutoff: Long,
)

data class LogDateChangeSet<T, D>(
    val changes: List<T>,
    val deletions: List<D>,
    val lastTimestamp: Long,
    val hasMore: Boolean = false,
)

data class LogDateEntryDeletion(
    val id: String,
    val deletedAt: Long,
)

data class LogDateJournalDeletion(
    val id: String,
    val deletedAt: Long,
)

data class LogDateAssociationRef(
    val journalId: String,
    val entryId: String,
)

data class LogDateAssociationDeletion(
    val association: LogDateAssociationRef,
    val deletedAt: Long,
)

/**
 * Internal collection repository boundary for the LogDate data model.
 *
 * Sync and AT Protocol adapters both consume this interface so neither of them owns the server's
 * internal persistence language.
 */
interface LogDateCollectionsRepository {
    suspend fun status(userId: UUID): LogDateCollectionsStatus

    suspend fun listEntries(userId: UUID): List<LogDateEntry>

    suspend fun upsertEntry(
        userId: UUID,
        entry: LogDateEntry,
    ): LogDateEntry

    suspend fun getEntry(
        userId: UUID,
        id: String,
    ): LogDateEntry?

    suspend fun deleteEntry(
        userId: UUID,
        id: String,
        deletedAt: Long,
    )

    suspend fun entryChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateEntry, LogDateEntryDeletion>

    suspend fun listJournals(userId: UUID): List<LogDateJournal>

    suspend fun upsertJournal(
        userId: UUID,
        journal: LogDateJournal,
    ): LogDateJournal

    suspend fun getJournal(
        userId: UUID,
        id: String,
    ): LogDateJournal?

    suspend fun deleteJournal(
        userId: UUID,
        id: String,
        deletedAt: Long,
    )

    suspend fun journalChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateJournal, LogDateJournalDeletion>

    suspend fun listAssociations(userId: UUID): List<LogDateAssociation>

    suspend fun upsertAssociations(
        userId: UUID,
        associations: List<LogDateAssociation>,
    ): List<LogDateAssociation>

    suspend fun deleteAssociations(
        userId: UUID,
        associations: List<LogDateAssociationRef>,
        deletedAt: Long,
    )

    suspend fun associationChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateAssociation, LogDateAssociationDeletion>

    suspend fun purgeTombstones(
        userId: UUID,
        olderThan: Long,
    ): LogDateCollectionsPurgeResult
}
