package app.logdate.server.space

import app.logdate.server.logging.SpacePurgeNotices
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.DAG_CBOR_CODEC
import studio.hypertext.atproto.repo.DagCborCodec
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A write of one sealed [record] to [author]'s repository in [space], made by the owner of [accountId].
 *
 * [blobs] are the CIDs of space blobs the record refers to. [swapCid], when set, requires the live
 * record to have that CID; [createOnly] requires that no live record exists under [rkey].
 */
data class SpaceRecordWrite(
    val accountId: UUID,
    val space: String,
    val author: String,
    val rkey: String,
    val record: JsonObject,
    val blobs: List<String>,
    val swapCid: String?,
    val createOnly: Boolean,
)

/** A live sealed record and the sequence number of its latest write. */
data class StoredSpaceRecord(
    val rkey: String,
    val cid: String,
    val seq: Long,
    val record: JsonObject,
)

/** The latest state of one key: a live record, or a tombstone with no [cid] or [record]. */
data class SpaceChange(
    val rkey: String,
    val seq: Long,
    val deleted: Boolean,
    val cid: String?,
    val record: JsonObject?,
)

/** A page of [changes] in sequence order. [cursor] is the sequence number to resume after. */
data class SpaceChangePage(
    val changes: List<SpaceChange>,
    val cursor: Long,
    val hasMore: Boolean,
)

/** A write or delete whose `swapCid` or `createOnly` condition no longer holds. */
class SpaceSwapException(
    message: String,
) : RuntimeException(message)

/** A write whose record isn't a valid sealed envelope; [reason] says why. */
class SpaceInvalidRecordException(
    val reason: String,
) : RuntimeException(reason)

/** A write that refers to a blob [cid] that isn't stored in the record's space. */
class SpaceMissingBlobException(
    val cid: String,
) : RuntimeException("Blob $cid isn't stored in this space")

/** The most changes one [SpaceStore.changes] call returns. */
const val MAX_SPACE_CHANGES_LIMIT = 500

/**
 * The most stored record bytes one [SpaceStore.changes] page carries, so a page of the largest
 * records can't exhaust memory. A page always holds at least one change, even a larger one.
 */
const val MAX_SPACE_CHANGES_BYTES: Long = 8L * 1024 * 1024

/** An encrypted blob uploaded to a space; its bytes are in object storage at [storagePath]. */
data class SpaceBlob(
    val cid: String,
    val mimeType: String,
    val size: Long,
    val storagePath: String,
)

/**
 * What one [SpaceStore.purge] did with unreferenced blobs. [blobsFailed] counts blobs whose object
 * couldn't be deleted from storage; their rows stay, so the next purge tries them again.
 */
data class SpacePurgeResult(
    val blobsDeleted: Int,
    val blobsFailed: Int,
)

/**
 * Stores sealed records for spaces. Each (space, author) pair has its own increasing sequence, so a
 * client can page through every change since the last sequence number it saw.
 */
interface SpaceStore {
    /**
     * Writes a record and returns it with its new sequence number. Throws [SpaceInvalidRecordException]
     * for a record that isn't a sealed envelope, [SpaceSwapException] when a write condition fails,
     * and [SpaceMissingBlobException] when the record refers to a blob the space doesn't hold.
     */
    suspend fun put(write: SpaceRecordWrite): StoredSpaceRecord

    /**
     * Replaces a live record with a tombstone and returns the tombstone's new sequence number. A key
     * that's already a tombstone returns that tombstone's sequence number; a key that never existed
     * changes nothing and returns the latest sequence number. Throws [SpaceSwapException] when
     * [swapCid] doesn't match a live record.
     */
    suspend fun delete(
        accountId: UUID,
        space: String,
        author: String,
        rkey: String,
        swapCid: String?,
    ): Long

    /** The live record under [rkey], or null if there's none. */
    suspend fun get(
        space: String,
        author: String,
        rkey: String,
    ): StoredSpaceRecord?

    /**
     * Up to [limit] changes with a sequence number greater than [since]; [limit] is 1 to
     * [MAX_SPACE_CHANGES_LIMIT]. The page ends early, with [SpaceChangePage.hasMore] set, rather than
     * carry more than [MAX_SPACE_CHANGES_BYTES] of stored records, but always holds at least one change.
     */
    suspend fun changes(
        space: String,
        author: String,
        since: Long,
        limit: Int,
    ): SpaceChangePage

    /**
     * Records a blob's metadata and returns what's stored. For a blob already in the space, it keeps
     * the original storage path and restarts the unreferenced grace period, so the returned
     * [SpaceBlob.storagePath] can differ from [blob]'s; the caller then deletes its own copy.
     */
    suspend fun putBlob(
        accountId: UUID,
        space: String,
        blob: SpaceBlob,
    ): SpaceBlob

    /** The metadata of the blob [cid] in [space], or null. */
    suspend fun getBlob(
        space: String,
        cid: String,
    ): SpaceBlob?

    /**
     * Deletes tombstones made before [tombstonesBefore], and blobs created before
     * [unreferencedBlobsBefore] that no record refers to. Each blob's object is deleted with
     * [deleteObject] while its row is locked, and the row goes only after that succeeds, so an object
     * is never left without a row pointing at it. When [deleteObject] throws, the failure is logged as
     * [SpacePurgeNotices.BLOB_DELETE_FAILED] and the row stays for the next purge; [deleteObject] must
     * therefore succeed for an object that's already gone. It blocks, and runs on [Dispatchers.IO].
     */
    suspend fun purge(
        tombstonesBefore: Instant,
        unreferencedBlobsBefore: Instant,
        deleteObject: (storagePath: String) -> Unit,
    ): SpacePurgeResult
}

/** The record's CID and its DAG-CBOR bytes. */
internal fun recordCid(record: JsonObject): Pair<String, ByteArray> {
    val bytes = DagCborCodec.encode(record)
    return Cid.sha256(DAG_CBOR_CODEC, bytes).value to bytes
}

/** Throws [SpaceInvalidRecordException] unless [record] is a valid sealed envelope. */
internal fun requireSealedRecord(record: JsonObject) {
    checkSealedRecord(record)?.let { throw SpaceInvalidRecordException(it) }
}

/** Throws unless [limit] is a page size [SpaceStore.changes] accepts. */
internal fun requireChangesLimit(limit: Int) {
    require(limit in 1..MAX_SPACE_CHANGES_LIMIT) { "limit must be 1 to $MAX_SPACE_CHANGES_LIMIT" }
}

/**
 * How many of the leading records, stored in [sizes] bytes each, fit in [budget] together. The first
 * record always counts, so a page never comes back empty while changes remain.
 */
internal fun countWithinBudget(
    sizes: List<Long>,
    budget: Long,
): Int {
    var total = 0L
    sizes.forEachIndexed { index, size ->
        total += size
        if (index > 0 && total > budget) return index
    }
    return sizes.size
}

/** Wraps a failed object delete inside a purge transaction, so the transaction rolls back and keeps the row. */
internal class SpaceObjectDeleteException(
    cause: Throwable,
) : RuntimeException(cause)

/** Deletes the object at [path], rethrowing a failure as [SpaceObjectDeleteException]. */
internal fun deleteObjectOrThrow(
    deleteObject: (storagePath: String) -> Unit,
    path: String,
) {
    try {
        deleteObject(path)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SpaceObjectDeleteException(e)
    }
}

/** Logs a blob whose object couldn't be deleted. Its row stays, so the next purge retries it. */
internal fun logObjectDeleteFailure(error: SpaceObjectDeleteException) {
    Napier.e(SpacePurgeNotices.BLOB_DELETE_FAILED, error.cause)
}

/** Decodes stored DAG-CBOR bytes, so every read returns the same form of a record. */
internal fun decodeRecord(bytes: ByteArray): JsonObject = DagCborCodec.decode(bytes).jsonObject

/**
 * A [SpaceStore] for servers running without a database. A page of changes carries at most
 * [changesByteBudget] stored record bytes.
 */
class InMemorySpaceStore(
    private val changesByteBudget: Long = MAX_SPACE_CHANGES_BYTES,
) : SpaceStore {
    private class Row(
        val seq: Long,
        val cid: String?,
        val bytes: ByteArray?,
        val deletedAt: Instant?,
    )

    private val mutex = Mutex()
    private val states = mutableMapOf<Pair<String, String>, Long>()
    private val records = mutableMapOf<Triple<String, String, String>, Row>()
    private val recordBlobs = mutableMapOf<Triple<String, String, String>, Set<String>>()
    private val blobs = mutableMapOf<Pair<String, String>, Pair<SpaceBlob, Instant>>()

    override suspend fun put(write: SpaceRecordWrite): StoredSpaceRecord =
        mutex.withLock {
            requireSealedRecord(write.record)
            val key = Triple(write.space, write.author, write.rkey)
            val live = records[key]?.takeIf { it.deletedAt == null }
            if (write.createOnly && live != null) throw SpaceSwapException("A record with this key exists")
            if (write.swapCid != null && live?.cid != write.swapCid) throw SpaceSwapException("The record changed")
            write.blobs
                .sorted()
                .firstOrNull { (write.space to it) !in blobs }
                ?.let { throw SpaceMissingBlobException(it) }
            val (cid, bytes) = recordCid(write.record)
            val seq = nextSeq(write.space, write.author)
            records[key] = Row(seq, cid, bytes, deletedAt = null)
            recordBlobs[key] = write.blobs.toSet()
            StoredSpaceRecord(write.rkey, cid, seq, decodeRecord(bytes))
        }

    override suspend fun delete(
        accountId: UUID,
        space: String,
        author: String,
        rkey: String,
        swapCid: String?,
    ): Long =
        mutex.withLock {
            val key = Triple(space, author, rkey)
            val row = records[key] ?: return@withLock states[space to author] ?: 0L
            if (row.deletedAt != null) return@withLock row.seq
            if (swapCid != null && row.cid != swapCid) throw SpaceSwapException("The record changed")
            val seq = nextSeq(space, author)
            records[key] = Row(seq, cid = null, bytes = null, deletedAt = Clock.System.now())
            recordBlobs.remove(key)
            seq
        }

    override suspend fun get(
        space: String,
        author: String,
        rkey: String,
    ): StoredSpaceRecord? =
        mutex.withLock {
            val row = records[Triple(space, author, rkey)] ?: return@withLock null
            val bytes = row.bytes ?: return@withLock null
            StoredSpaceRecord(rkey, requireNotNull(row.cid), row.seq, decodeRecord(bytes))
        }

    override suspend fun changes(
        space: String,
        author: String,
        since: Long,
        limit: Int,
    ): SpaceChangePage {
        requireChangesLimit(limit)
        return mutex.withLock {
            val rows =
                records
                    .filter { (key, row) -> key.first == space && key.second == author && row.seq > since }
                    .entries
                    .sortedBy { it.value.seq }
                    .take(limit + 1)
            val fitting = countWithinBudget(rows.take(limit).map { (it.value.bytes?.size ?: 0).toLong() }, changesByteBudget)
            val page =
                rows.take(fitting).map { (key, row) ->
                    SpaceChange(key.third, row.seq, row.deletedAt != null, row.cid, row.bytes?.let(::decodeRecord))
                }
            SpaceChangePage(page, page.lastOrNull()?.seq ?: since, rows.size > fitting)
        }
    }

    override suspend fun putBlob(
        accountId: UUID,
        space: String,
        blob: SpaceBlob,
    ): SpaceBlob =
        mutex.withLock {
            val storagePath = blobs[space to blob.cid]?.first?.storagePath ?: blob.storagePath
            val stored = blob.copy(storagePath = storagePath)
            blobs[space to blob.cid] = stored to Clock.System.now()
            stored
        }

    override suspend fun getBlob(
        space: String,
        cid: String,
    ): SpaceBlob? = mutex.withLock { blobs[space to cid]?.first }

    override suspend fun purge(
        tombstonesBefore: Instant,
        unreferencedBlobsBefore: Instant,
        deleteObject: (storagePath: String) -> Unit,
    ): SpacePurgeResult {
        val candidates =
            mutex.withLock {
                records.entries.removeAll { (_, row) -> row.deletedAt != null && row.deletedAt < tombstonesBefore }
                blobs.keys.filter { purgeable(it, unreferencedBlobsBefore) }.sortedWith(compareBy({ it.first }, { it.second }))
            }
        var deleted = 0
        var failed = 0
        candidates.forEach { key ->
            mutex.withLock {
                if (!purgeable(key, unreferencedBlobsBefore)) return@withLock
                val path = blobs.getValue(key).first.storagePath
                try {
                    withContext(Dispatchers.IO) { deleteObjectOrThrow(deleteObject, path) }
                } catch (e: SpaceObjectDeleteException) {
                    logObjectDeleteFailure(e)
                    failed++
                    return@withLock
                }
                blobs.remove(key)
                deleted++
            }
        }
        return SpacePurgeResult(deleted, failed)
    }

    /** Whether the blob [key] still exists, was stored before [createdBefore], and no record refers to it. */
    private fun purgeable(
        key: Pair<String, String>,
        createdBefore: Instant,
    ): Boolean {
        val stored = blobs[key] ?: return false
        if (stored.second >= createdBefore) return false
        return recordBlobs.none { (recordKey, cids) -> recordKey.first == key.first && key.second in cids }
    }

    private fun nextSeq(
        space: String,
        author: String,
    ): Long {
        val seq = (states[space to author] ?: 0L) + 1
        states[space to author] = seq
        return seq
    }
}
