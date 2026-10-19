package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.canonicalRecordKey
import app.logdate.client.sync.space.decodeRecord
import app.logdate.client.sync.space.encodeRecord
import app.logdate.client.sync.space.parseDatetime
import app.logdate.client.sync.space.parseUuidKey
import app.logdate.client.sync.space.recordRef
import app.logdate.client.sync.space.sameRecordKey
import app.logdate.client.sync.space.toDatetime
import app.logdate.lexicon.app.logdate.DefsRecordRef
import app.logdate.lexicon.app.logdate.journal.Entry
import app.logdate.lexicon.app.logdate.journal.Membership
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant
import kotlin.uuid.Uuid
import app.logdate.lexicon.app.logdate.journal.Journal as JournalRecord

/**
 * An entry's place in a journal, as the personal space stores it.
 *
 * On the device this is a `journal_content_links` row, which doesn't record when the entry was
 * added. A membership this device writes therefore leaves [createdAt] out, so every device that
 * adds the same entry to the same journal writes exactly the same record. A time another app
 * recorded is read and written back unchanged.
 *
 * @property journalId The journal the entry belongs to.
 * @property entryId The entry, which is the note with this ID.
 * @property createdAt When the entry was added to the journal, if the app that added it recorded it.
 * @property syncVersion The record's position in the space's change log, or 0 before it's written.
 */
data class JournalMembership(
    val journalId: Uuid,
    val entryId: Uuid,
    val createdAt: Instant? = null,
    val syncVersion: Long = 0,
)

/**
 * Converts memberships to and from `app.logdate.journal.membership` records.
 *
 * The record key is `<journal id>:<entry id>`, so a journal and an entry have at most one
 * membership and every device that adds the same entry to the same journal writes the same record.
 * A record whose key doesn't match the journal and entry it names is refused, because it would
 * stand for a different membership than the one its key claims.
 */
class MembershipRecordMapper : SpaceRecordMapper<JournalMembership> {
    override val collection: String = Membership.TYPE

    override fun rkeyOf(value: JournalMembership): String = rkeyFor(value.journalId, value.entryId)

    override fun toRecord(value: JournalMembership): JsonObject {
        val owner = "$collection/${rkeyOf(value)}"
        return encodeRecord(
            Membership.serializer(),
            Membership(
                journal = recordRef(JournalRecord.TYPE, value.journalId),
                entry = recordRef(Entry.TYPE, value.entryId),
                createdAt = value.createdAt?.toDatetime("$owner createdAt"),
            ),
        )
    }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): JournalMembership {
        val owner = "$collection/$rkey"
        val membership = decodeRecord(Membership.serializer(), collection, rkey, record)
        val journalId = idOf(membership.journal, JournalRecord.TYPE, "journal", owner)
        val entryId = idOf(membership.entry, Entry.TYPE, "entry", owner)
        if (!sameRecordKey(rkey, rkeyFor(journalId, entryId))) {
            throw SpaceRecordMappingException("$owner: its key doesn't name the journal and entry the record refers to")
        }
        return JournalMembership(
            journalId = journalId,
            entryId = entryId,
            createdAt = membership.createdAt?.let { parseDatetime("$owner createdAt", it) },
        )
    }

    private fun idOf(
        ref: DefsRecordRef,
        expectedCollection: String,
        field: String,
        owner: String,
    ): Uuid {
        if (ref.collection != expectedCollection) {
            throw SpaceRecordMappingException("$owner: its $field is a ${ref.collection}, not a $expectedCollection")
        }
        return parseUuidKey(expectedCollection, ref.rkey)
    }

    companion object {
        /** The record key of the membership of [entryId] in [journalId]. */
        fun rkeyFor(
            journalId: Uuid,
            entryId: Uuid,
        ): String = canonicalRecordKey("$journalId:$entryId")
    }
}
