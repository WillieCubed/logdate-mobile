package app.logdate.server.space

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** An encrypted blob uploaded to a space; its bytes are in object storage at [storagePath]. */
data class SpaceBlob(
    val cid: String,
    val mimeType: String,
    val size: Long,
    val storagePath: String,
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

    /** Up to [limit] changes with a sequence number greater than [since]; [limit] is 1 to [MAX_SPACE_CHANGES_LIMIT]. */
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
     * [unreferencedBlobsBefore] that no record refers to. Returns the deleted blobs' storage paths,
     * which the caller deletes from object storage.
     */
    suspend fun purge(
        tombstonesBefore: Instant,
        unreferencedBlobsBefore: Instant,
    ): List<String>
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

/** Decodes stored DAG-CBOR bytes, so every read returns the same form of a record. */
internal fun decodeRecord(bytes: ByteArray): JsonObject = DagCborCodec.decode(bytes).jsonObject

/** A [SpaceStore] for servers running without a database. */
class InMemorySpaceStore : SpaceStore {
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
            val page =
                rows.take(limit).map { (key, row) ->
                    SpaceChange(key.third, row.seq, row.deletedAt != null, row.cid, row.bytes?.let(::decodeRecord))
                }
            SpaceChangePage(page, page.lastOrNull()?.seq ?: since, rows.size > limit)
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
    ): List<String> =
        mutex.withLock {
            records.entries.removeAll { (_, row) -> row.deletedAt != null && row.deletedAt < tombstonesBefore }
            val referenced = recordBlobs.flatMap { (key, cids) -> cids.map { key.first to it } }.toSet()
            val purged =
                blobs.filter { (key, value) -> value.second < unreferencedBlobsBefore && key !in referenced }
            purged.keys.forEach(blobs::remove)
            purged.values.map { it.first.storagePath }
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
