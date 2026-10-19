package app.logdate.server.database

import app.logdate.server.space.MAX_SPACE_CHANGES_BYTES
import app.logdate.server.space.SpaceBlob
import app.logdate.server.space.SpaceChange
import app.logdate.server.space.SpaceChangePage
import app.logdate.server.space.SpaceMissingBlobException
import app.logdate.server.space.SpaceObjectDeleteException
import app.logdate.server.space.SpacePurgeResult
import app.logdate.server.space.SpaceRecordWrite
import app.logdate.server.space.SpaceStore
import app.logdate.server.space.SpaceSwapException
import app.logdate.server.space.StoredSpaceRecord
import app.logdate.server.space.countWithinBudget
import app.logdate.server.space.decodeRecord
import app.logdate.server.space.deleteObjectOrThrow
import app.logdate.server.space.logObjectDeleteFailure
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
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
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

/** How many purge candidates one query loads. */
private const val PURGE_BATCH_SIZE = 500

/**
 * Stores sealed space records in PostgreSQL. Every write locks its (space, author) state row before
 * taking the next sequence number, so concurrent writes never share one. A page of changes carries
 * at most [changesByteBudget] stored record bytes.
 */
class PostgreSQLSpaceStore(
    private val changesByteBudget: Long = MAX_SPACE_CHANGES_BYTES,
) : SpaceStore {
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
            // Sizes first, so only the records that fit the byte budget are loaded. Repeatable read
            // makes the second query see exactly the rows the first one measured.
            transaction(transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ) {
                val after =
                    (SpaceRecordsTable.spaceUri eq space) and (SpaceRecordsTable.authorDid eq author) and
                        (SpaceRecordsTable.seq greater since)
                val sizes =
                    SpaceRecordsTable
                        .select(SpaceRecordsTable.seq, SpaceRecordsTable.sizeBytes)
                        .where { after }
                        .orderBy(SpaceRecordsTable.seq to SortOrder.ASC)
                        .limit(limit + 1)
                        .map { it[SpaceRecordsTable.seq] to it[SpaceRecordsTable.sizeBytes].toLong() }
                val fitting = countWithinBudget(sizes.take(limit).map { it.second }, changesByteBudget)
                if (fitting == 0) return@transaction SpaceChangePage(emptyList(), since, false)
                val page =
                    SpaceRecordsTable
                        .selectAll()
                        .where { after and (SpaceRecordsTable.seq lessEq sizes[fitting - 1].first) }
                        .orderBy(SpaceRecordsTable.seq to SortOrder.ASC)
                        .map { it.toChange() }
                SpaceChangePage(page, page.lastOrNull()?.seq ?: since, sizes.size > fitting)
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
        deleteObject: (storagePath: String) -> Unit,
    ): SpacePurgeResult =
        withContext(Dispatchers.IO) {
            transaction {
                SpaceRecordsTable.deleteWhere { (deleted eq true) and (deletedAt less tombstonesBefore) }
            }
            var deleted = 0
            var failed = 0
            var after: Pair<String, String>? = null
            do {
                val batch = purgeCandidates(unreferencedBlobsBefore, after)
                batch.forEach { (space, cid) ->
                    when (purgeBlob(space, cid, unreferencedBlobsBefore, deleteObject)) {
                        BlobPurge.DELETED -> deleted++
                        BlobPurge.FAILED -> failed++
                        BlobPurge.KEPT -> Unit
                    }
                }
                after = batch.lastOrNull()
            } while (batch.size == PURGE_BATCH_SIZE)
            SpacePurgeResult(deleted, failed)
        }

    /**
     * The next [PURGE_BATCH_SIZE] blobs, in key order after [after], that were created before
     * [createdBefore] and that no record refers to. Paging by key rather than restarting from the
     * first keeps a blob whose object delete failed from being picked again in the same purge.
     */
    private fun purgeCandidates(
        createdBefore: Instant,
        after: Pair<String, String>?,
    ): List<Pair<String, String>> =
        transaction {
            SpaceBlobsTable
                .select(SpaceBlobsTable.spaceUri, SpaceBlobsTable.cid)
                .where {
                    val candidate = (SpaceBlobsTable.createdAt less createdBefore) and unreferenced()
                    if (after == null) {
                        candidate
                    } else {
                        val (space, cid) = after
                        candidate and
                            (
                                (SpaceBlobsTable.spaceUri greater space) or
                                    ((SpaceBlobsTable.spaceUri eq space) and (SpaceBlobsTable.cid greater cid))
                            )
                    }
                }.orderBy(SpaceBlobsTable.spaceUri to SortOrder.ASC, SpaceBlobsTable.cid to SortOrder.ASC)
                .limit(PURGE_BATCH_SIZE)
                .map { it[SpaceBlobsTable.spaceUri] to it[SpaceBlobsTable.cid] }
        }

    private enum class BlobPurge { DELETED, KEPT, FAILED }

    /**
     * Deletes one unreferenced blob: its object first, then its row, inside one transaction that holds
     * the row lock throughout. A write that refers to the blob locks its row first, so taking the same
     * lock waits for that write to commit; the check after it is a separate statement, so under read
     * committed it sees the committed reference and keeps the blob. When the object delete fails, the
     * transaction rolls back and the row stays for the next purge.
     */
    private fun purgeBlob(
        space: String,
        cid: String,
        createdBefore: Instant,
        deleteObject: (storagePath: String) -> Unit,
    ): BlobPurge =
        try {
            transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                val key = (SpaceBlobsTable.spaceUri eq space) and (SpaceBlobsTable.cid eq cid)
                SpaceBlobsTable
                    .select(SpaceBlobsTable.cid)
                    .where { key }
                    .forUpdate()
                    .singleOrNull() ?: return@transaction BlobPurge.KEPT
                val path =
                    SpaceBlobsTable
                        .select(SpaceBlobsTable.storagePath)
                        .where { key and (SpaceBlobsTable.createdAt less createdBefore) and unreferenced() }
                        .singleOrNull()
                        ?.get(SpaceBlobsTable.storagePath) ?: return@transaction BlobPurge.KEPT
                deleteObjectOrThrow(deleteObject, path)
                SpaceBlobsTable.deleteWhere { key }
                BlobPurge.DELETED
            }
        } catch (e: SpaceObjectDeleteException) {
            logObjectDeleteFailure(e)
            BlobPurge.FAILED
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
