package app.logdate.server.logdate

import io.github.aakira.napier.Napier
import studio.hypertext.atproto.identity.AtprotoDid
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal data class LogDateCollectionsState(
    val repoDid: AtprotoDid,
    val lastVersion: Long,
)

internal data class LogDateCollectionMetadata(
    val recordKey: String,
    val version: Long,
    val deletedAt: Long?,
)

internal data class LogDateCollectionChangesMetadata(
    val changes: List<LogDateCollectionMetadata>,
    val deletions: List<LogDateCollectionMetadata>,
    val lastTimestamp: Long,
    val hasMore: Boolean,
)

internal interface LogDateCollectionsMetadataStore {
    suspend fun status(userId: UUID): LogDateCollectionsStatus

    suspend fun state(userId: UUID): LogDateCollectionsState?

    suspend fun listLive(
        userId: UUID,
        collection: LogDateCollectionKind,
    ): List<LogDateCollectionMetadata>

    suspend fun metadata(
        userId: UUID,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata?

    suspend fun changes(
        userId: UUID,
        collection: LogDateCollectionKind,
        since: Long,
        limit: Int,
    ): LogDateCollectionChangesMetadata

    suspend fun upsert(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata

    /**
     * Upserts every key in [recordKeys] as one atomic unit: either all of them get a fresh
     * server version, or (on failure partway through) none of them do.
     *
     * [RepoBackedLogDateCollectionsRepository.upsertAssociations] commits every association's
     * repo record in a single atomic batch write *before* this runs. A naive per-record loop
     * here would let a mid-batch failure leave some records with metadata and the rest without -
     * "phantom" repo records that are present in commit history but invisible to [listLive] and
     * [changes], which read from this store rather than the tree. Atomicity here lets the caller
     * compensate by deleting the whole repo batch on any failure, keeping the repo and this store
     * in lockstep instead of just bounding how bad the divergence gets.
     *
     * The default is the naive loop, which is correct when nothing fails but gives none of that
     * guarantee - implementations that can offer a real all-or-nothing batch (a DB transaction,
     * for example) should override it.
     */
    suspend fun upsertBatch(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordKeys: List<String>,
    ): List<LogDateCollectionMetadata> = recordKeys.map { recordKey -> upsert(userId, repoDid, collection, recordKey) }

    suspend fun delete(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordKey: String,
        deletedAt: Long,
    ): LogDateCollectionMetadata?

    suspend fun purgeTombstones(
        userId: UUID,
        olderThan: Long,
    ): LogDateCollectionsPurgeResult

    /**
     * Purges expired tombstones for every user.
     *
     * The scheduled retention job has no user context, so it needs a tenant-wide sweep rather
     * than the per-user purge the maintenance endpoint uses.
     */
    suspend fun purgeAllTombstones(olderThan: Long): LogDateCollectionsPurgeResult
}

internal class InMemoryLogDateCollectionsMetadataStore : LogDateCollectionsMetadataStore {
    private val initializedAt = System.currentTimeMillis()
    private val states = ConcurrentHashMap<UUID, LogDateCollectionsState>()
    private val lastVersions = ConcurrentHashMap<UUID, AtomicLong>()
    private val metadata = ConcurrentHashMap<UUID, ConcurrentHashMap<LogDateCollectionKey, LogDateCollectionRow>>()

    override suspend fun status(userId: UUID): LogDateCollectionsStatus {
        val rows = metadataForUser(userId).values
        return LogDateCollectionsStatus(
            entryCount = rows.count { it.collection == LogDateCollectionKind.ENTRY && !it.deleted },
            journalCount = rows.count { it.collection == LogDateCollectionKind.JOURNAL && !it.deleted },
            associationCount = rows.count { it.collection == LogDateCollectionKind.ASSOCIATION && !it.deleted },
            lastTimestamp = states[userId]?.lastVersion ?: initializedAt,
        )
    }

    override suspend fun state(userId: UUID): LogDateCollectionsState? = states[userId]

    override suspend fun listLive(
        userId: UUID,
        collection: LogDateCollectionKind,
    ): List<LogDateCollectionMetadata> =
        metadataForUser(userId)
            .values
            .filter { it.collection == collection && !it.deleted }
            .sortedBy { it.version }
            .map(LogDateCollectionRow::toMetadata)

    override suspend fun metadata(
        userId: UUID,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata? =
        metadataForUser(userId)[LogDateCollectionKey(collection = collection, recordKey = recordKey)]
            ?.takeUnless(LogDateCollectionRow::deleted)
            ?.toMetadata()

    override suspend fun changes(
        userId: UUID,
        collection: LogDateCollectionKind,
        since: Long,
        limit: Int,
    ): LogDateCollectionChangesMetadata {
        val rows =
            metadataForUser(userId)
                .values
                .filter { it.collection == collection && it.version > since }
        val changeRows = rows.filterNot(LogDateCollectionRow::deleted).sortedBy { it.version }
        val deletionRows = rows.filter(LogDateCollectionRow::deleted).sortedBy { it.version }
        val limitedChanges = changeRows.take(limit)
        val limitedDeletions = deletionRows.take(limit)
        val lastTimestamp =
            listOfNotNull(
                limitedChanges.maxOfOrNull { it.version },
                limitedDeletions.maxOfOrNull { it.version },
            ).maxOrNull() ?: since
        return LogDateCollectionChangesMetadata(
            changes = limitedChanges.map(LogDateCollectionRow::toMetadata),
            deletions = limitedDeletions.map(LogDateCollectionRow::toMetadata),
            lastTimestamp = lastTimestamp,
            hasMore = changeRows.size > limit || deletionRows.size > limit,
        )
    }

    override suspend fun upsert(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata = upsertRow(userId = userId, repoDid = repoDid, collection = collection, recordKey = recordKey)

    override suspend fun upsertBatch(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordKeys: List<String>,
    ): List<LogDateCollectionMetadata> {
        val userMetadata = metadataForUser(userId)
        val keys = recordKeys.map { LogDateCollectionKey(collection = collection, recordKey = it) }
        // Snapshot only the rows this batch might touch, and the version state, so a failure
        // partway through can put back exactly what was there before - no more, no less - rather
        // than leaving any of this batch's upserts behind as a record with no matching commit
        // anywhere else.
        val previousRows = keys.associateWith { userMetadata[it] }
        val previousState = states[userId]

        return try {
            recordKeys.map { recordKey -> upsertRow(userId = userId, repoDid = repoDid, collection = collection, recordKey = recordKey) }
        } catch (failure: Throwable) {
            keys.forEach { key ->
                val previousRow = previousRows.getValue(key)
                if (previousRow == null) {
                    userMetadata.remove(key)
                } else {
                    userMetadata[key] = previousRow
                }
            }
            if (previousState == null) states.remove(userId) else states[userId] = previousState
            throw failure
        }
    }

    private fun upsertRow(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): LogDateCollectionMetadata {
        val version = nextVersion(userId = userId, repoDid = repoDid)
        val row =
            LogDateCollectionRow(
                collection = collection,
                recordKey = recordKey,
                version = version,
                deleted = false,
                deletedAt = null,
            )
        metadataForUser(userId)[LogDateCollectionKey(collection = collection, recordKey = recordKey)] = row
        return row.toMetadata()
    }

    override suspend fun delete(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordKey: String,
        deletedAt: Long,
    ): LogDateCollectionMetadata? {
        val key = LogDateCollectionKey(collection = collection, recordKey = recordKey)
        if (metadataForUser(userId)[key] == null) {
            return null
        }
        val version = nextVersion(userId = userId, repoDid = repoDid)
        val row =
            LogDateCollectionRow(
                collection = collection,
                recordKey = recordKey,
                version = version,
                deleted = true,
                deletedAt = deletedAt,
            )
        metadataForUser(userId)[key] = row
        return row.toMetadata()
    }

    override suspend fun purgeTombstones(
        userId: UUID,
        olderThan: Long,
    ): LogDateCollectionsPurgeResult {
        val rows = metadataForUser(userId)
        val removed =
            rows
                .filterValues { row -> row.deleted && (row.deletedAt ?: Long.MAX_VALUE) < olderThan }
                .values
        removed.forEach { row ->
            rows.remove(LogDateCollectionKey(collection = row.collection, recordKey = row.recordKey))
        }
        return LogDateCollectionsPurgeResult(
            entryPurged = removed.count { it.collection == LogDateCollectionKind.ENTRY },
            journalPurged = removed.count { it.collection == LogDateCollectionKind.JOURNAL },
            associationPurged = removed.count { it.collection == LogDateCollectionKind.ASSOCIATION },
            cutoff = olderThan,
        )
    }

    override suspend fun purgeAllTombstones(olderThan: Long): LogDateCollectionsPurgeResult {
        var entry = 0
        var journal = 0
        var association = 0
        metadata.values.forEach { rows ->
            val removed =
                rows
                    .filterValues { row -> row.deleted && (row.deletedAt ?: Long.MAX_VALUE) < olderThan }
                    .values
                    .toList()
            removed.forEach { row ->
                rows.remove(LogDateCollectionKey(collection = row.collection, recordKey = row.recordKey))
                when (row.collection) {
                    LogDateCollectionKind.ENTRY -> entry++
                    LogDateCollectionKind.JOURNAL -> journal++
                    LogDateCollectionKind.ASSOCIATION -> association++
                    else -> Unit
                }
            }
        }
        return LogDateCollectionsPurgeResult(
            entryPurged = entry,
            journalPurged = journal,
            associationPurged = association,
            cutoff = olderThan,
        )
    }

    private fun metadataForUser(userId: UUID): ConcurrentHashMap<LogDateCollectionKey, LogDateCollectionRow> =
        metadata.getOrPut(userId) { ConcurrentHashMap() }

    private fun nextVersion(
        userId: UUID,
        repoDid: AtprotoDid,
    ): Long {
        val counter = lastVersions.getOrPut(userId) { AtomicLong(initializedAt) }
        val candidate = System.currentTimeMillis()
        val version =
            counter.updateAndGet { previous ->
                when {
                    candidate > previous -> candidate
                    else -> previous + 1L
                }
            }
        states.compute(userId) { _, existing ->
            when {
                existing == null -> LogDateCollectionsState(repoDid = repoDid, lastVersion = version)
                existing.repoDid == repoDid -> existing.copy(lastVersion = version)
                else -> {
                    Napier.w("Preserving canonical repo DID ${existing.repoDid} for $userId instead of replacing it with $repoDid")
                    existing.copy(lastVersion = version)
                }
            }
        }
        return version
    }
}

internal data class LogDateCollectionKey(
    val collection: LogDateCollectionKind,
    val recordKey: String,
)

internal data class LogDateCollectionRow(
    val collection: LogDateCollectionKind,
    val recordKey: String,
    val version: Long,
    val deleted: Boolean,
    val deletedAt: Long?,
) {
    fun toMetadata(): LogDateCollectionMetadata =
        LogDateCollectionMetadata(
            recordKey = recordKey,
            version = version,
            deletedAt = deletedAt,
        )
}
