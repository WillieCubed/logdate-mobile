package app.logdate.server.database

import app.logdate.server.space.SpaceBlob
import app.logdate.server.space.SpaceChange
import app.logdate.server.space.SpaceChangePage
import app.logdate.server.space.SpaceMissingBlobException
import app.logdate.server.space.SpaceRecordWrite
import app.logdate.server.space.SpaceStore
import app.logdate.server.space.SpaceSwapException
import app.logdate.server.space.StoredSpaceRecord
import app.logdate.server.space.decodeRecord
import app.logdate.server.space.recordCid
import app.logdate.server.space.requireChangesLimit
import app.logdate.server.space.requireSealedRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

private const val PUT_BLOB_ATTEMPTS = 3

/**
 * Stores sealed space records in PostgreSQL. Every write locks its (space, author) state row before
 * taking the next sequence number, so concurrent writes never share one.
 */
class PostgreSQLSpaceStore : SpaceStore {
    override suspend fun put(write: SpaceRecordWrite): StoredSpaceRecord {
        requireSealedRecord(write.record)
        return withContext(Dispatchers.IO) {
            transaction {
                val lastSeq = lockState(write.accountId, write.space, write.author)
                val existing = lockedRecordRow(write.space, write.author, write.rkey)
                val liveCid = existing?.takeUnless { it[SpaceRecordsTable.deleted] }?.get(SpaceRecordsTable.cid)
                if (write.createOnly && liveCid != null) throw SpaceSwapException("A record with this key exists")
                if (write.swapCid != null && liveCid != write.swapCid) throw SpaceSwapException("The record changed")
                lockBlobs(write.space, write.blobs)
                val (cid, bytes) = recordCid(write.record)
                val seq = advanceState(write.space, write.author, lastSeq)
                if (existing == null) {
                    SpaceRecordsTable.insert {
                        it[spaceUri] = write.space
                        it[authorDid] = write.author
                        it[rkey] = write.rkey
                        it[accountId] = write.accountId
                        it[SpaceRecordsTable.seq] = seq
                        it[SpaceRecordsTable.cid] = cid
                        it[record] = bytes
                        it[sizeBytes] = bytes.size
                        it[deleted] = false
                        it[deletedAt] = null
                    }
                } else {
                    SpaceRecordsTable.update({ recordKey(write.space, write.author, write.rkey) }) {
                        it[SpaceRecordsTable.seq] = seq
                        it[SpaceRecordsTable.cid] = cid
                        it[record] = bytes
                        it[sizeBytes] = bytes.size
                        it[deleted] = false
                        it[deletedAt] = null
                    }
                }
                SpaceRecordBlobsTable.deleteWhere { blobKey(write.space, write.author, write.rkey) }
                SpaceRecordBlobsTable.batchInsert(write.blobs.distinct(), shouldReturnGeneratedValues = false) { blobCid ->
                    this[SpaceRecordBlobsTable.spaceUri] = write.space
                    this[SpaceRecordBlobsTable.authorDid] = write.author
                    this[SpaceRecordBlobsTable.rkey] = write.rkey
                    this[SpaceRecordBlobsTable.cid] = blobCid
                }
                StoredSpaceRecord(write.rkey, cid, seq, decodeRecord(bytes))
            }
        }
    }

    override suspend fun delete(
        accountId: UUID,
        space: String,
        author: String,
        rkey: String,
        swapCid: String?,
    ): Long =
        withContext(Dispatchers.IO) {
            transaction {
                val lastSeq = lockState(accountId, space, author)
                val existing = lockedRecordRow(space, author, rkey) ?: return@transaction lastSeq
                if (existing[SpaceRecordsTable.deleted]) return@transaction existing[SpaceRecordsTable.seq]
                if (swapCid != null && existing[SpaceRecordsTable.cid] != swapCid) throw SpaceSwapException("The record changed")
                val seq = advanceState(space, author, lastSeq)
                SpaceRecordsTable.update({ recordKey(space, author, rkey) }) {
                    it[SpaceRecordsTable.seq] = seq
                    it[cid] = null
                    it[record] = null
                    it[sizeBytes] = 0
                    it[deleted] = true
                    it[deletedAt] = Clock.System.now()
                }
                SpaceRecordBlobsTable.deleteWhere { blobKey(space, author, rkey) }
                seq
            }
        }

    override suspend fun get(
        space: String,
        author: String,
        rkey: String,
    ): StoredSpaceRecord? =
        withContext(Dispatchers.IO) {
            transaction {
                val row = recordRow(space, author, rkey) ?: return@transaction null
                val bytes = row[SpaceRecordsTable.record] ?: return@transaction null
                StoredSpaceRecord(rkey, requireNotNull(row[SpaceRecordsTable.cid]), row[SpaceRecordsTable.seq], decodeRecord(bytes))
            }
        }

    override suspend fun changes(
        space: String,
        author: String,
        since: Long,
        limit: Int,
    ): SpaceChangePage {
        requireChangesLimit(limit)
        return withContext(Dispatchers.IO) {
            transaction {
                val rows =
                    SpaceRecordsTable
                        .selectAll()
                        .where {
                            (SpaceRecordsTable.spaceUri eq space) and (SpaceRecordsTable.authorDid eq author) and
                                (SpaceRecordsTable.seq greater since)
                        }.orderBy(SpaceRecordsTable.seq to SortOrder.ASC)
                        .limit(limit + 1)
                        .toList()
                val page = rows.take(limit).map { it.toChange() }
                SpaceChangePage(page, page.lastOrNull()?.seq ?: since, rows.size > limit)
            }
        }
    }

    override suspend fun putBlob(
        accountId: UUID,
        space: String,
        blob: SpaceBlob,
    ): SpaceBlob =
        withContext(Dispatchers.IO) {
            repeat(PUT_BLOB_ATTEMPTS) {
                transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) { tryPutBlob(accountId, space, blob) }
                    ?.let { return@withContext it }
            }
            error("Blob ${blob.cid} was purged during every attempt to store it")
        }

    /**
     * Inserts the blob row, or locks and refreshes the existing one and returns it with its original
     * storage path. Returns null when the existing row was purged between the two statements, so the
     * caller tries again rather than answering success without a row.
     */
    private fun tryPutBlob(
        accountId: UUID,
        space: String,
        blob: SpaceBlob,
    ): SpaceBlob? {
        val now = Clock.System.now()
        val inserted =
            SpaceBlobsTable
                .insertIgnore {
                    it[spaceUri] = space
                    it[cid] = blob.cid
                    it[SpaceBlobsTable.accountId] = accountId
                    it[mimeType] = blob.mimeType
                    it[sizeBytes] = blob.size
                    it[storagePath] = blob.storagePath
                    it[createdAt] = now
                }.insertedCount
        if (inserted > 0) return blob
        val key = (SpaceBlobsTable.spaceUri eq space) and (SpaceBlobsTable.cid eq blob.cid)
        val existingPath =
            SpaceBlobsTable
                .select(SpaceBlobsTable.storagePath)
                .where { key }
                .forUpdate()
                .singleOrNull()
                ?.get(SpaceBlobsTable.storagePath) ?: return null
        SpaceBlobsTable.update({ key }) {
            it[mimeType] = blob.mimeType
            it[sizeBytes] = blob.size
            it[createdAt] = now
        }
        return blob.copy(storagePath = existingPath)
    }

    override suspend fun getBlob(
        space: String,
        cid: String,
    ): SpaceBlob? =
        withContext(Dispatchers.IO) {
            transaction {
                SpaceBlobsTable
                    .selectAll()
                    .where { (SpaceBlobsTable.spaceUri eq space) and (SpaceBlobsTable.cid eq cid) }
                    .singleOrNull()
                    ?.let {
                        SpaceBlob(
                            it[SpaceBlobsTable.cid],
                            it[SpaceBlobsTable.mimeType],
                            it[SpaceBlobsTable.sizeBytes],
                            it[SpaceBlobsTable.storagePath],
                        )
                    }
            }
        }

    override suspend fun purge(
        tombstonesBefore: Instant,
        unreferencedBlobsBefore: Instant,
    ): List<String> =
        withContext(Dispatchers.IO) {
            transaction {
                SpaceRecordsTable.deleteWhere { (deleted eq true) and (deletedAt less tombstonesBefore) }
            }
            val candidates =
                transaction {
                    SpaceBlobsTable
                        .select(SpaceBlobsTable.spaceUri, SpaceBlobsTable.cid)
                        .where { (SpaceBlobsTable.createdAt less unreferencedBlobsBefore) and unreferenced() }
                        .orderBy(SpaceBlobsTable.spaceUri to SortOrder.ASC, SpaceBlobsTable.cid to SortOrder.ASC)
                        .map { it[SpaceBlobsTable.spaceUri] to it[SpaceBlobsTable.cid] }
                }
            candidates.mapNotNull { (space, cid) -> purgeBlob(space, cid, unreferencedBlobsBefore) }
        }

    /**
     * Deletes one unreferenced blob row and returns its storage path, or null if it's gone or now
     * referenced. A write that refers to the blob locks its row first, so taking the same lock waits
     * for that write to commit; the delete is a separate statement, so under read committed it sees
     * the committed reference and keeps the blob.
     */
    private fun purgeBlob(
        space: String,
        cid: String,
        createdBefore: Instant,
    ): String? =
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val key = (SpaceBlobsTable.spaceUri eq space) and (SpaceBlobsTable.cid eq cid)
            val path =
                SpaceBlobsTable
                    .select(SpaceBlobsTable.storagePath)
                    .where { key }
                    .forUpdate()
                    .singleOrNull()
                    ?.get(SpaceBlobsTable.storagePath) ?: return@transaction null
            val deleted = SpaceBlobsTable.deleteWhere { key and (createdAt less createdBefore) and unreferenced() }
            path.takeIf { deleted > 0 }
        }

    /**
     * Locks the space blobs [cids] refers to, in key order, and throws [SpaceMissingBlobException] for
     * one that doesn't exist. Purging takes the same row lock, so it can't delete a blob this write
     * is about to refer to.
     */
    private fun lockBlobs(
        space: String,
        cids: List<String>,
    ) {
        val wanted = cids.distinct().sorted()
        if (wanted.isEmpty()) return
        val found =
            SpaceBlobsTable
                .select(SpaceBlobsTable.cid)
                .where { (SpaceBlobsTable.spaceUri eq space) and (SpaceBlobsTable.cid inList wanted) }
                .orderBy(SpaceBlobsTable.cid to SortOrder.ASC)
                .forUpdate()
                .map { it[SpaceBlobsTable.cid] }
                .toSet()
        wanted.firstOrNull { it !in found }?.let { throw SpaceMissingBlobException(it) }
    }

    /** Creates the state row if needed, locks it, and returns its last sequence number. */
    private fun lockState(
        accountId: UUID,
        space: String,
        author: String,
    ): Long {
        SpaceStatesTable.insertIgnore {
            it[spaceUri] = space
            it[authorDid] = author
            it[SpaceStatesTable.accountId] = accountId
            it[lastSeq] = 0L
        }
        return SpaceStatesTable
            .selectAll()
            .where { stateKey(space, author) }
            .forUpdate()
            .single()[SpaceStatesTable.lastSeq]
    }

    private fun advanceState(
        space: String,
        author: String,
        lastSeq: Long,
    ): Long {
        val next = lastSeq + 1
        SpaceStatesTable.update({ stateKey(space, author) }) { it[SpaceStatesTable.lastSeq] = next }
        return next
    }

    private fun recordRow(
        space: String,
        author: String,
        rkey: String,
    ): ResultRow? =
        SpaceRecordsTable
            .selectAll()
            .where { recordKey(space, author, rkey) }
            .singleOrNull()

    /** Locks the row too, so purging can't remove a tombstone this transaction is about to rewrite. */
    private fun lockedRecordRow(
        space: String,
        author: String,
        rkey: String,
    ): ResultRow? =
        SpaceRecordsTable
            .selectAll()
            .where { recordKey(space, author, rkey) }
            .forUpdate()
            .singleOrNull()

    private fun stateKey(
        space: String,
        author: String,
    ): Op<Boolean> = (SpaceStatesTable.spaceUri eq space) and (SpaceStatesTable.authorDid eq author)

    private fun recordKey(
        space: String,
        author: String,
        rkey: String,
    ): Op<Boolean> = (SpaceRecordsTable.spaceUri eq space) and (SpaceRecordsTable.authorDid eq author) and (SpaceRecordsTable.rkey eq rkey)

    private fun blobKey(
        space: String,
        author: String,
        rkey: String,
    ): Op<Boolean> =
        (SpaceRecordBlobsTable.spaceUri eq space) and (SpaceRecordBlobsTable.authorDid eq author) and (SpaceRecordBlobsTable.rkey eq rkey)

    private fun unreferenced(): Op<Boolean> =
        notExists(
            SpaceRecordBlobsTable
                .select(SpaceRecordBlobsTable.cid)
                .where {
                    (SpaceRecordBlobsTable.spaceUri eq SpaceBlobsTable.spaceUri) and (SpaceRecordBlobsTable.cid eq SpaceBlobsTable.cid)
                },
        )

    private fun ResultRow.toChange(): SpaceChange =
        SpaceChange(
            rkey = this[SpaceRecordsTable.rkey],
            seq = this[SpaceRecordsTable.seq],
            deleted = this[SpaceRecordsTable.deleted],
            cid = this[SpaceRecordsTable.cid],
            record = this[SpaceRecordsTable.record]?.let(::decodeRecord),
        )
}
