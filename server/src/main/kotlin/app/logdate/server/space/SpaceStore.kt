package app.logdate.server.space

import app.logdate.server.logging.SpacePurgeNotices
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.DAG_CBOR_CODEC
import studio.hypertext.atproto.repo.DagCborCodec
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A write of one sealed [record] to [author]'s repository in [space], made by the owner of [accountId].
 *
 * [blobs] are the CIDs of space blobs the record refers to. [swapCid], when set, requires the live
 * record to have that CID; [createOnly] requires that no live record exists under [rkey]. With a
 * [quota], a write that would take the account past it is refused.
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
    val quota: SpaceQuota? = null,
)

/**
 * An account's storage quota as it applies to its spaces: [limitBytes] in all, of which media and
 * backups already take [otherBytes].
 */
data class SpaceQuota(
    val limitBytes: Long,
    val otherBytes: Long,
)

/**
 * A write or upload that would take the account past its storage quota. [usedBytes] is everything
 * counted against [limitBytes] when it was refused, including room held for uploads in progress.
 */
class SpaceQuotaExceededException(
    val limitBytes: Long,
    val usedBytes: Long,
) : RuntimeException("Storage quota exceeded: $usedBytes of $limitBytes bytes in use")

/**
 * Room for [bytes] held against [accountId]'s quota while an upload is in progress, so concurrent
 * uploads can't together pass it. [SpaceStore.putBlob] uses it up; [SpaceStore.releaseUpload] gives
 * it back. One the server never comes back for stops counting after [UPLOAD_RESERVATION_LIFETIME].
 */
data class SpaceUploadReservation(
    val id: UUID,
    val accountId: UUID,
    val bytes: Long,
)

/**
 * How long a reservation holds room when its upload never finishes, as when an instance stops
 * mid-upload. Cloud Run ends a request after its timeout, 60 seconds on LogDate Cloud, so this is
 * that and a margin. An upload that outlives its reservation is still checked when its blob is stored.
 */
internal val UPLOAD_RESERVATION_LIFETIME: Duration = 2.minutes

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

/**
 * A [SpaceStore.changes] cursor older than tombstones that purging has deleted, so paging on from it
 * would miss those deletions. The client lists again from zero; [purgedThroughSeq] is the highest
 * purged sequence number.
 */
class SpaceCursorTooOldException(
    val purgedThroughSeq: Long,
) : RuntimeException("Changes up to $purgedThroughSeq were purged")

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
 *
 * The store keeps a running total of each account's stored bytes, the DAG-CBOR size of its live
 * records plus the size of its blobs, and changes it in the same step as the data it counts, so the
 * total never drifts and a quota check reads one number. Writes and uploads check a [SpaceQuota] in
 * that same step, so concurrent requests from one account can't together pass it.
 */
interface SpaceStore {
    /**
     * Writes a record and returns it with its new sequence number. Throws [SpaceInvalidRecordException]
     * for a record that isn't a sealed envelope, [SpaceSwapException] when a write condition fails,
     * [SpaceMissingBlobException] when the record refers to a blob the space doesn't hold, and
     * [SpaceQuotaExceededException] when the record is larger than the one it replaces by more than
     * [SpaceRecordWrite.quota] has room for. Conditions are checked before the quota, so a write that
     * fails both is a swap conflict.
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
     * Throws [SpaceCursorTooOldException] when [since] is above zero but below the highest sequence
     * number of a purged tombstone; listing from zero always works.
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
     *
     * A new blob counts towards the account's stored bytes, and throws [SpaceQuotaExceededException]
     * when [quota] has no room for it; a blob the space already holds adds nothing. [reservation], the
     * room held for this upload, is used up in the same step and doesn't count against the check.
     */
    suspend fun putBlob(
        accountId: UUID,
        space: String,
        blob: SpaceBlob,
        quota: SpaceQuota? = null,
        reservation: SpaceUploadReservation? = null,
    ): SpaceBlob

    /**
     * Holds room for [bytes] against [quota] for an upload about to start, or throws
     * [SpaceQuotaExceededException] when stored bytes and other uploads in progress leave too little.
     */
    suspend fun reserveUpload(
        accountId: UUID,
        bytes: Long,
        quota: SpaceQuota,
    ): SpaceUploadReservation

    /** Gives back the room [reservation] held. Releasing one that [putBlob] used up, or twice, does nothing. */
    suspend fun releaseUpload(reservation: SpaceUploadReservation)

    /** The bytes [accountId]'s live space records and blobs take in storage. */
    suspend fun storedBytes(accountId: UUID): Long

    /** The metadata of the blob [cid] in [space], or null. */
    suspend fun getBlob(
        space: String,
        cid: String,
    ): SpaceBlob?

    /**
     * Deletes tombstones made before [tombstonesBefore], remembering the highest purged sequence number
     * of each repository for [changes], and blobs created before
     * [unreferencedBlobsBefore] that no record refers to. Each blob's object is deleted with
     * [deleteObject] while its row is locked, and the row goes only after that succeeds, so an object
     * is never left without a row pointing at it. When [deleteObject] throws or doesn't finish within
     * the store's object delete timeout, the failure is logged as
     * [SpacePurgeNotices.BLOB_DELETE_FAILED] and the row stays for the next purge; [deleteObject] must
     * therefore succeed for an object that's already gone. It blocks, and runs on [Dispatchers.IO].
     * Deleted blobs stop counting towards their account's stored bytes, and upload reservations past
     * their lifetime are dropped.
     */
    suspend fun purge(
        tombstonesBefore: Instant,
        unreferencedBlobsBefore: Instant,
        deleteObject: (storagePath: String) -> Unit,
    ): SpacePurgeResult
}

/**
 * Throws [SpaceQuotaExceededException] when adding [growthBytes] to the [spaceBytes] an account
 * already uses or holds would pass [quota]. Growth of zero or less always fits, so an account over
 * its quota can still shrink and delete what it has.
 */
internal fun requireRoom(
    quota: SpaceQuota?,
    spaceBytes: Long,
    growthBytes: Long,
) {
    if (quota == null || growthBytes <= 0) return
    val used = quota.otherBytes + spaceBytes
    if (used + growthBytes > quota.limitBytes) throw SpaceQuotaExceededException(quota.limitBytes, used)
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

/** Throws [SpaceCursorTooOldException] when [since] predates tombstones purged through [purgedThroughSeq]. */
internal fun requireFreshCursor(
    since: Long,
    purgedThroughSeq: Long,
) {
    if (since in 1 until purgedThroughSeq) throw SpaceCursorTooOldException(purgedThroughSeq)
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

/** How long a purge waits for one object delete before it gives up and keeps the blob's row. */
internal val OBJECT_DELETE_TIMEOUT: Duration = 30.seconds

/** Wraps a failed object delete inside a purge transaction, so the transaction rolls back and keeps the row. */
internal class SpaceObjectDeleteException(
    cause: Throwable,
) : RuntimeException(cause)

/** An object delete that didn't finish within [timeout]. */
internal class SpaceObjectDeleteTimeoutException(
    timeout: Duration,
) : RuntimeException("The object delete didn't finish within $timeout")

/**
 * Deletes the object at [path], rethrowing a failure as [SpaceObjectDeleteException]. The delete runs
 * on [Dispatchers.IO] and is waited for at most [timeout]; past that it is interrupted and abandoned,
 * and the failure's cause is a [SpaceObjectDeleteTimeoutException]. An abandoned delete that ignores
 * the interrupt can still remove the object afterwards.
 */
internal fun deleteObjectOrThrow(
    deleteObject: (storagePath: String) -> Unit,
    path: String,
    timeout: Duration,
) {
    val deletion = FutureTask { deleteObject(path) }
    Dispatchers.IO.asExecutor().execute(deletion)
    try {
        deletion.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        deletion.cancel(true)
        throw SpaceObjectDeleteException(SpaceObjectDeleteTimeoutException(timeout))
    } catch (e: ExecutionException) {
        val cause = e.cause
        if (cause !is Exception || cause is CancellationException) throw cause ?: e
        throw SpaceObjectDeleteException(cause)
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
 * [changesByteBudget] stored record bytes, and a purge waits at most [objectDeleteTimeout] for each
 * object delete. One lock guards everything, so quota checks and the totals they read change together.
 */
class InMemorySpaceStore(
    private val changesByteBudget: Long = MAX_SPACE_CHANGES_BYTES,
    private val objectDeleteTimeout: Duration = OBJECT_DELETE_TIMEOUT,
) : SpaceStore {
    private class Row(
        val accountId: UUID,
        val seq: Long,
        val cid: String?,
        val bytes: ByteArray?,
        val deletedAt: Instant?,
    )

    private class BlobRow(
        val accountId: UUID,
        val blob: SpaceBlob,
        val createdAt: Instant,
    )

    private class Held(
        val accountId: UUID,
        val bytes: Long,
        val expiresAt: Instant,
    )

    private val mutex = Mutex()
    private val states = mutableMapOf<Pair<String, String>, Long>()
    private val purgedThrough = mutableMapOf<Pair<String, String>, Long>()
    private val records = mutableMapOf<Triple<String, String, String>, Row>()
    private val recordBlobs = mutableMapOf<Triple<String, String, String>, Set<String>>()
    private val blobs = mutableMapOf<Pair<String, String>, BlobRow>()
    private val stored = mutableMapOf<UUID, Long>()
    private val reservations = mutableMapOf<UUID, Held>()

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
            val growth = bytes.size.toLong() - (live?.bytes?.size ?: 0)
            requireRoom(write.quota, held(write.accountId, except = null), growth)
            addStored(write.accountId, growth)
            val seq = nextSeq(write.space, write.author)
            records[key] = Row(write.accountId, seq, cid, bytes, deletedAt = null)
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
            addStored(row.accountId, -(row.bytes?.size ?: 0).toLong())
            records[key] = Row(row.accountId, seq, cid = null, bytes = null, deletedAt = Clock.System.now())
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
            requireFreshCursor(since, purgedThrough[space to author] ?: 0L)
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
        quota: SpaceQuota?,
        reservation: SpaceUploadReservation?,
    ): SpaceBlob =
        mutex.withLock {
            val existing = blobs[space to blob.cid]
            if (existing == null) {
                requireRoom(quota, held(accountId, except = reservation?.id), blob.size)
                addStored(accountId, blob.size)
            }
            reservation?.let { reservations.remove(it.id) }
            val stored = blob.copy(storagePath = existing?.blob?.storagePath ?: blob.storagePath)
            blobs[space to blob.cid] = BlobRow(existing?.accountId ?: accountId, stored, Clock.System.now())
            stored
        }

    override suspend fun reserveUpload(
        accountId: UUID,
        bytes: Long,
        quota: SpaceQuota,
    ): SpaceUploadReservation =
        mutex.withLock {
            requireRoom(quota, held(accountId, except = null), bytes)
            val reservation = SpaceUploadReservation(UUID.randomUUID(), accountId, bytes)
            reservations[reservation.id] = Held(accountId, bytes, Clock.System.now() + UPLOAD_RESERVATION_LIFETIME)
            reservation
        }

    override suspend fun releaseUpload(reservation: SpaceUploadReservation) {
        mutex.withLock { reservations.remove(reservation.id) }
    }

    override suspend fun storedBytes(accountId: UUID): Long = mutex.withLock { stored[accountId] ?: 0L }

    override suspend fun getBlob(
        space: String,
        cid: String,
    ): SpaceBlob? = mutex.withLock { blobs[space to cid]?.blob }

    override suspend fun purge(
        tombstonesBefore: Instant,
        unreferencedBlobsBefore: Instant,
        deleteObject: (storagePath: String) -> Unit,
    ): SpacePurgeResult {
        val candidates =
            mutex.withLock {
                val now = Clock.System.now()
                reservations.values.removeAll { it.expiresAt <= now }
                val old = records.filterValues { it.deletedAt != null && it.deletedAt < tombstonesBefore }
                old.forEach { (key, row) ->
                    val repository = key.first to key.second
                    purgedThrough[repository] = maxOf(purgedThrough[repository] ?: 0L, row.seq)
                    records.remove(key)
                }
                blobs.keys.filter { purgeable(it, unreferencedBlobsBefore) }.sortedWith(compareBy({ it.first }, { it.second }))
            }
        var deleted = 0
        var failed = 0
        candidates.forEach { key ->
            mutex.withLock {
                if (!purgeable(key, unreferencedBlobsBefore)) return@withLock
                val row = blobs.getValue(key)
                try {
                    withContext(Dispatchers.IO) { deleteObjectOrThrow(deleteObject, row.blob.storagePath, objectDeleteTimeout) }
                } catch (e: SpaceObjectDeleteException) {
                    logObjectDeleteFailure(e)
                    failed++
                    return@withLock
                }
                blobs.remove(key)
                addStored(row.accountId, -row.blob.size)
                deleted++
            }
        }
        return SpacePurgeResult(deleted, failed)
    }

    /** What [accountId] stores plus the room its unexpired uploads hold, leaving out the reservation [except]. */
    private fun held(
        accountId: UUID,
        except: UUID?,
    ): Long {
        val now = Clock.System.now()
        val reserved =
            reservations
                .filter { (id, held) -> id != except && held.accountId == accountId && held.expiresAt > now }
                .values
                .sumOf { it.bytes }
        return (stored[accountId] ?: 0L) + reserved
    }

    private fun addStored(
        accountId: UUID,
        delta: Long,
    ) {
        if (delta == 0L) return
        val current = stored[accountId] ?: 0L
        val next = current + delta
        if (next < 0) {
            Napier.e("Storage total would go below zero: account=$accountId kind=SPACE current=$current delta=$delta; stored 0")
        }
        stored[accountId] = next.coerceAtLeast(0L)
    }

    /** Whether the blob [key] still exists, was stored before [createdBefore], and no record refers to it. */
    private fun purgeable(
        key: Pair<String, String>,
        createdBefore: Instant,
    ): Boolean {
        val row = blobs[key] ?: return false
        if (row.createdAt >= createdBefore) return false
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
