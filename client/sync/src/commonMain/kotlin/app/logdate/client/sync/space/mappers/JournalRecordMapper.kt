package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceMediaResolver
import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.blobCidFor
import app.logdate.client.sync.space.canonicalRecordKey
import app.logdate.client.sync.space.decodeRecord
import app.logdate.client.sync.space.encodeRecord
import app.logdate.client.sync.space.mediaFor
import app.logdate.client.sync.space.parseDatetime
import app.logdate.client.sync.space.parseUuidKey
import app.logdate.client.sync.space.recordRef
import app.logdate.client.sync.space.toDatetime
import app.logdate.lexicon.app.logdate.DefsRecordRef
import app.logdate.shared.model.Journal
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid
import app.logdate.lexicon.app.logdate.journal.Journal as JournalRecord

/**
 * A journal as the personal space stores it: either a journal the person uses, or one they merged
 * into another journal.
 *
 * On the device a merged journal is no longer a journal. The merge moves its entries to the
 * surviving journal and removes it, and the device remembers a redirect from the one to the other.
 * In the space the merged journal stays as a record with `mergedInto` set, so a device that still
 * has entries in it can move them.
 *
 * @property journal The journal. For a merged journal, the journal as it was when it was merged.
 * @property mergedInto The surviving journal this one was merged into, or null if it wasn't merged.
 */
data class SpaceJournal(
    val journal: Journal,
    val mergedInto: Uuid? = null,
)

/**
 * Converts journals to and from `app.logdate.journal.journal` records, keyed by the journal's id.
 *
 * An empty description and an unfavorited journal are written by leaving the field out, which the
 * record reads the same way. A cover is the journal's `coverImageUri`, which must already name a
 * file uploaded to the personal space; until it is, the journal can't be written and the failure
 * is retryable.
 *
 * This is the owner's own copy of a journal. A copy shared with other people must leave `favorite`
 * out, because the flag is the owner's private choice.
 *
 * What a record can't hold:
 * - `syncVersion` is the old sync's version, not part of the journal: the transport applies the
 *   record's position in the space's change log instead, so [fromRecord] returns 0.
 *
 * A record with `mergedInto` reads as a [SpaceJournal] with [SpaceJournal.mergedInto] set, which
 * the download applies as a merge instead of storing it as a journal. A journal that names itself
 * as the one it was merged into is refused.
 *
 * @param media connects the journal's cover with its uploaded blob.
 */
class JournalRecordMapper(
    private val media: SpaceMediaResolver = SpaceMediaResolver.Default,
) : SpaceRecordMapper<SpaceJournal> {
    override val collection: String = JournalRecord.TYPE

    override fun rkeyOf(value: SpaceJournal): String = canonicalRecordKey(value.journal.id.toString())

    override fun toRecord(value: SpaceJournal): JsonObject {
        val journal = value.journal
        val owner = "$collection/${journal.id}"
        return encodeRecord(
            JournalRecord.serializer(),
            JournalRecord(
                title = journal.title,
                description = journal.description.ifEmpty { null },
                cover = journal.coverImageUri?.let { media.mediaFor(it, owner) },
                favorite = journal.isFavorited.takeIf { it },
                mergedInto = value.mergedInto?.let { recordRef(collection, it) },
                createdAt = journal.created.toDatetime("$owner createdAt"),
                updatedAt = journal.lastUpdated.toDatetime("$owner updatedAt"),
            ),
        )
    }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): SpaceJournal {
        val owner = "$collection/$rkey"
        val id = parseUuidKey(collection, rkey)
        val journal = decodeRecord(JournalRecord.serializer(), collection, rkey, record)
        val created = parseDatetime("$owner createdAt", journal.createdAt)
        return SpaceJournal(
            journal =
                Journal(
                    id = id,
                    title = journal.title,
                    description = journal.description.orEmpty(),
                    isFavorited = journal.favorite == true,
                    created = created,
                    lastUpdated = journal.updatedAt?.let { parseDatetime("$owner updatedAt", it) } ?: created,
                    coverImageUri = journal.cover?.let { media.downloaded(id, it) },
                ),
            mergedInto = journal.mergedInto?.let { mergedIntoOf(it, id, owner) },
        )
    }

    override fun blobCidsOf(value: SpaceJournal): List<String> =
        value.journal.coverImageUri
            ?.let { listOf(media.blobCidFor(it, "$collection/${value.journal.id}")) }
            .orEmpty()

    private fun mergedIntoOf(
        ref: DefsRecordRef,
        id: Uuid,
        owner: String,
    ): Uuid {
        if (ref.collection != collection) throw SpaceRecordMappingException("$owner: it was merged into a ${ref.collection}, not a journal")
        val survivor = parseUuidKey(collection, ref.rkey)
        if (survivor == id) throw SpaceRecordMappingException("$owner: it was merged into itself")
        return survivor
    }
}
