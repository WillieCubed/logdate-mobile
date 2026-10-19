package app.logdate.client.sync.space

import app.logdate.client.sync.metadata.UploadScope
import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import app.logdate.space.ChangePage
import app.logdate.space.DataHome
import app.logdate.space.DataHomeConflictException
import app.logdate.space.DataHomeCursorTooOldException
import app.logdate.space.RecordChange
import app.logdate.space.RecordSwap
import app.logdate.space.SealedRecordCodec
import app.logdate.space.SealingKeys
import app.logdate.space.SpaceRef
import app.logdate.space.StoredRecord
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.xrpc.XrpcProtocolException

internal const val TEST_DID = "did:plc:spaceowner"
internal val TEST_IDENTITY_KEY = ByteArray(32) { (it * 3).toByte() }
internal val TEST_SPACE = SpaceRef.personal(TEST_DID)
internal const val JOURNAL_COLLECTION = "app.logdate.journal.journal"

/**
 * An in-memory data home that keeps a data home's promises: swap conditions are enforced, every
 * write and delete takes the next sequence number, the change feed lists the latest change for each
 * key in sequence order, and a cursor older than [purgedThrough] is refused as too old.
 */
internal class FakeDataHome {
    private class Entry(
        val rkey: String,
        val seq: Long,
        val cid: String?,
        val record: Sealed?,
    )

    private val entries = linkedMapOf<String, Entry>()
    private var seq = 0L
    val blobs = mutableMapOf<String, ByteArray>()

    /** Tokens the home accepts; any other is answered `401 ExpiredToken`. */
    val acceptedTokens = mutableSetOf("test-access-token", "new-token")

    /** Failures to answer the next calls with, in order, before doing anything. */
    val failures = ArrayDeque<Throwable>()

    /** Deletions at or below this sequence number have been purged. */
    var purgedThrough = 0L

    val tokensSeen = mutableListOf<String>()
    var writes = 0
        private set

    /** How many times the change feed was read. */
    var listChangesCalls = 0
        private set

    fun liveRkeys(): Set<String> =
        entries.values
            .filter { it.record != null }
            .map { it.rkey }
            .toSet()

    fun stored(rkey: String): StoredRecord? =
        entries[rkey]
            ?.takeIf {
                it.record != null
            }?.let { StoredRecord(it.rkey, it.cid!!, it.seq, it.record!!) }

    /** What another device of the owner writes: [record] sealed into the space under [rkey]. */
    suspend fun putFromAnotherDevice(
        collection: String,
        rkey: String,
        record: JsonObject,
    ): StoredRecord {
        val sealed = SealedRecordCodec.seal(testKeys(), TEST_SPACE, TEST_DID, SealedPayload(collection, record, rkey))
        return write(sealed.rkey, sealed.record, RecordSwap.Unconditional)
    }

    suspend fun deleteFromAnotherDevice(
        collection: String,
        rkey: String,
    ) {
        delete(SealedRecordCodec.sealedRkey(testKeys(), collection, rkey), RecordSwap.Unconditional)
    }

    /** Removes [rkey] and purges its deletion, as a server's cleanup does. */
    fun deleteAndPurge(rkey: String) {
        entries.remove(rkey)
        seq += 1
        purgedThrough = seq
    }

    fun signedIn(token: String): DataHome = SignedIn(token)

    private fun write(
        rkey: String,
        record: Sealed,
        swap: RecordSwap,
    ): StoredRecord {
        val current = entries[rkey]?.takeIf { it.record != null }
        when (swap) {
            RecordSwap.Absent -> if (current != null) throw DataHomeConflictException("The record exists")
            is RecordSwap.Matching -> if (current?.cid != swap.cid) throw DataHomeConflictException("The record changed")
            RecordSwap.Unconditional -> Unit
        }
        seq += 1
        writes += 1
        val cid = "bafyrecord$seq"
        entries.remove(rkey)
        entries[rkey] = Entry(rkey, seq, cid, record)
        return StoredRecord(rkey, cid, seq, record)
    }

    private fun delete(
        rkey: String,
        swap: RecordSwap,
    ): Long {
        val current = entries[rkey]?.takeIf { it.record != null }
        if (swap is RecordSwap.Matching && current?.cid != swap.cid) throw DataHomeConflictException("The record changed")
        if (current == null) return seq
        seq += 1
        entries.remove(rkey)
        entries[rkey] = Entry(rkey, seq, null, null)
        return seq
    }

    private inner class SignedIn(
        private val token: String,
    ) : DataHome {
        private fun check() {
            tokensSeen += token
            failures.removeFirstOrNull()?.let { throw it }
            if (token !in acceptedTokens) throw XrpcProtocolException(401, "ExpiredToken", "The access token expired")
        }

        override suspend fun putRecord(
            space: SpaceRef,
            rkey: String,
            record: Sealed,
            blobs: List<String>,
            swap: RecordSwap,
        ): StoredRecord {
            check()
            return write(rkey, record, swap)
        }

        override suspend fun deleteRecord(
            space: SpaceRef,
            rkey: String,
            swap: RecordSwap,
        ): Long {
            check()
            return delete(rkey, swap)
        }

        override suspend fun getRecord(
            space: SpaceRef,
            rkey: String,
        ): StoredRecord? {
            check()
            return stored(rkey)
        }

        override suspend fun listChanges(
            space: SpaceRef,
            since: Long,
            limit: Int,
        ): ChangePage {
            listChangesCalls += 1
            check()
            if (since in 1 until purgedThrough) throw DataHomeCursorTooOldException("List again from 0")
            val after = entries.values.filter { it.seq > since }.sortedBy { it.seq }
            val page = after.take(limit)
            val changes = page.map { RecordChange(it.rkey, it.seq, it.record == null, it.cid, it.record) }
            return ChangePage(changes, page.lastOrNull()?.seq ?: since, after.size > limit)
        }

        override suspend fun uploadBlob(
            space: SpaceRef,
            bytes: ByteArray,
        ): String {
            check()
            val cid = Cid.rawSha256(bytes).value
            this@FakeDataHome.blobs[cid] = bytes
            return cid
        }

        override suspend fun getBlob(
            space: SpaceRef,
            cid: String,
        ): ByteArray? {
            check()
            return this@FakeDataHome.blobs[cid]
        }

        override suspend fun uploadBlob(
            space: SpaceRef,
            sizeBytes: Long,
            open: () -> RawSource,
        ): String {
            check()
            val bytes = open().buffered().use { it.readByteArray() }
            require(bytes.size.toLong() == sizeBytes) { "Declared $sizeBytes bytes, sent ${bytes.size}" }
            val cid = Cid.rawSha256(bytes).value
            this@FakeDataHome.blobs[cid] = bytes
            return cid
        }

        override suspend fun downloadBlob(
            space: SpaceRef,
            cid: String,
            sink: RawSink,
        ): Boolean {
            check()
            val bytes = this@FakeDataHome.blobs[cid] ?: return false
            val buffer = Buffer().apply { write(bytes) }
            sink.write(buffer, buffer.size)
            sink.flush()
            return true
        }
    }
}

internal suspend fun testKeys(): SealingKeys = SealingKeys.personal(TEST_IDENTITY_KEY)

internal class InMemorySpaceSwapStore : SpaceSwapStore {
    val states = mutableMapOf<Pair<UploadScope, Pair<String, String>>, SpaceRecordState>()

    override suspend fun get(
        scope: UploadScope,
        collection: String,
        rkey: String,
    ): SpaceRecordState? = states[scope to (collection to rkey)]

    override suspend fun bySealedRkey(
        scope: UploadScope,
        sealedRkey: String,
    ): SpaceRecordState? = states.entries.firstOrNull { it.key.first == scope && it.value.sealedRkey == sealedRkey }?.value

    override suspend fun all(scope: UploadScope): List<SpaceRecordState> = states.filterKeys { it.first == scope }.values.toList()

    override suspend fun put(
        scope: UploadScope,
        state: SpaceRecordState,
    ) {
        states[scope to (state.collection to state.rkey)] = state
    }

    override suspend fun delete(
        scope: UploadScope,
        collection: String,
        rkey: String,
    ) {
        states.remove(scope to (collection to rkey))
    }
}

internal fun journalRecord(
    title: String,
    updatedAt: String = "2026-10-18T10:00:00Z",
): JsonObject =
    buildJsonObject {
        put("\$type", JOURNAL_COLLECTION)
        put("title", title)
        put("createdAt", "2026-10-01T09:00:00Z")
        put("updatedAt", updatedAt)
    }
