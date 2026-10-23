package app.logdate.client.sync.space.migration

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceAccess
import app.logdate.client.sync.space.SpaceRecordInvalidException
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.outgoingRecords
import app.logdate.shared.model.sync.SpaceMigrationDigests
import app.logdate.space.SealedRecordCodec
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.lexicon.LexiconRecordValidator
import studio.hypertext.atproto.lexicon.data.LexiconData
import kotlin.coroutines.cancellation.CancellationException

/** A record this device holds an item for, as the personal space should hold it. */
internal class ExpectedRecord(
    val entityType: EntityType,
    val entityId: String,
    val collection: String,
    val rkey: String,
    val contentHash: String,
)

/**
 * A live record the personal space holds, opened. [collection], [rkey] and [contentHash] are null
 * when the record can't be opened on this device, such as one sealed with another key.
 */
internal class StoredCopy(
    val cid: String,
    val seq: Long,
    val collection: String?,
    val rkey: String?,
    val contentHash: String?,
)

/** Everything the personal space holds, read back from the start of its change log. */
internal class SpaceReadBack(
    val lastSeq: Long,
    val live: Map<String, StoredCopy>,
) {
    /** The digest the switch sends: every live record as `<rkey> <cid>`, as the server computes it. */
    fun digest(): String = sha256Hex(SpaceMigrationDigests.spaceText(live.map { (rkey, copy) -> rkey to copy.cid }))
}

/** How the personal space differs from what this device holds, by sealed key. */
internal class SpaceDifference(
    val missing: List<ExpectedRecord>,
    val differing: List<ExpectedRecord>,
    val extra: List<String>,
) {
    val isEmpty: Boolean get() = missing.isEmpty() && differing.isEmpty() && extra.isEmpty()

    /** The items whose records have to be uploaded again. */
    val itemsToUpload: Set<Pair<EntityType, String>> get() = (missing + differing).map { it.entityType to it.entityId }.toSet()

    /**
     * The kind with the most differing items and how many, for reporting. Records this device has no
     * item for are counted as entries, since nothing here says what they were.
     */
    fun summary(): JournalMoveProblem.CopyDiffers {
        val items =
            (missing + differing).groupBy { it.entityType.dataKind() }.mapValues { (_, records) ->
                records.map { it.entityId }.distinct().size
            }
        val worst = items.maxByOrNull { it.value } ?: return JournalMoveProblem.CopyDiffers(JournalDataKind.ENTRIES, extra.size)
        return JournalMoveProblem.CopyDiffers(worst.key, worst.value)
    }
}

/**
 * Builds what the personal space should hold from this device's items, reads back what it does hold,
 * and compares the two record by record, by a hash of each record's content.
 */
internal class SpaceMigrationReadBackCheck(
    private val writer: SpaceMigrationWriter,
    private val access: SpaceAccess,
    private val validator: LexiconRecordValidator,
) {
    /** The records every item of [kinds] is stored as, by sealed key. */
    suspend fun expected(kinds: List<MigrationKind>): Map<String, ExpectedRecord> {
        val expected = mutableMapOf<String, ExpectedRecord>()
        var unsupported: Pair<JournalDataKind, Int>? = null
        for (kind in kinds) {
            var failed = 0
            for (id in kind.orderedIds()) {
                val item = kind.kind.local.load(id) ?: continue
                val records =
                    try {
                        kind.kind.outgoingRecords(kind.prepare(item), validator)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: SpaceRecordMappingException) {
                        if (error.retryable) throw error
                        failed++
                        continue
                    } catch (_: SpaceRecordInvalidException) {
                        failed++
                        continue
                    }
                for (record in records) {
                    val sealed = writer.sealedRkey(record.collection, record.rkey)
                    expected[sealed] = ExpectedRecord(kind.entityType, id, record.collection, record.rkey, contentHash(record.record))
                }
            }
            if (failed > 0 && unsupported == null) unsupported = kind.dataKind to failed
        }
        unsupported?.let { (kind, count) -> throw MigrationUnsupportedItemsException(kind, count) }
        return expected
    }

    /** Every live record the personal space holds, opened where this device can open it. */
    suspend fun readBack(): SpaceReadBack {
        val live = mutableMapOf<String, StoredCopy>()
        var since = 0L
        var lastSeq = 0L
        do {
            val page = writer.changes(since)
            for (change in page.changes) {
                lastSeq = maxOf(lastSeq, change.seq)
                val record = change.record
                if (change.deleted || record == null || change.cid == null) {
                    live.remove(change.rkey)
                    continue
                }
                live[change.rkey] = open(change.rkey, requireNotNull(change.cid), change.seq, record)
            }
            if (page.hasMore && page.cursor <= since) error("The personal space's change log stopped moving forward")
            since = page.cursor
        } while (page.hasMore)
        return SpaceReadBack(lastSeq, live)
    }

    fun compare(
        expected: Map<String, ExpectedRecord>,
        actual: SpaceReadBack,
    ): SpaceDifference {
        val missing = expected.filterKeys { it !in actual.live }.values.toList()
        val differing =
            expected.mapNotNull { (sealed, record) ->
                val stored = actual.live[sealed] ?: return@mapNotNull null
                record.takeIf {
                    stored.collection != record.collection || stored.rkey != record.rkey || stored.contentHash != record.contentHash
                }
            }
        val extra = actual.live.keys.filter { it !in expected }
        return SpaceDifference(missing, differing, extra)
    }

    private suspend fun open(
        sealedRkey: String,
        cid: String,
        seq: Long,
        record: app.logdate.lexicon.app.logdate.crypto.Sealed,
    ): StoredCopy =
        try {
            val payload = SealedRecordCodec.open(access.keys, access.space, access.author, sealedRkey, record)
            val content = payload.record as? JsonObject
            StoredCopy(cid, seq, payload.collection, payload.rkey.lowercase(), content?.let(::contentHash))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            StoredCopy(cid, seq, null, null, null)
        }
}

/** A hash of [record]'s content: SHA-256 of its deterministic DAG-CBOR encoding. */
internal fun contentHash(record: JsonObject): String =
    CryptographyProvider.Default
        .get(SHA256)
        .hasher()
        .hashBlocking(LexiconData.encodeToDagCbor(JsonElement.serializer(), record))
        .toHex()

internal fun sha256Hex(text: String): String =
    CryptographyProvider.Default
        .get(SHA256)
        .hasher()
        .hashBlocking(text.encodeToByteArray())
        .toHex()

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
