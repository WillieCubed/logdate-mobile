package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.decodeRecord
import app.logdate.client.sync.space.encodeRecord
import app.logdate.client.sync.space.parseDatetime
import app.logdate.client.sync.space.sameRecordKey
import app.logdate.client.sync.space.toDatetime
import app.logdate.lexicon.app.logdate.journal.Entry
import app.logdate.lexicon.app.logdate.place.EntryLink
import app.logdate.shared.model.location.VisitMemoryLink
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.json.JsonObject

/**
 * Maps a [VisitMemoryLink], an entry the person connected to a visit, to an
 * `app.logdate.place.entryLink` record and back.
 *
 * The record key is the entry's key and the lowercase hexadecimal SHA-256 of the evidence id,
 * joined by a colon, so linking the same entry to the same visit twice, on any device, writes one
 * record. Links made before the personal space are stored under random ids instead; moving them
 * over gives each its derived key, and two links with the same entry and visit become one record.
 */
object PlaceEntryLinkRecordMapper : SpaceRecordMapper<VisitMemoryLink> {
    override val collection: String = EntryLink.TYPE

    override fun rkeyOf(value: VisitMemoryLink): String = keyOf(value.noteId, value.targetEvidenceId)

    override fun toRecord(value: VisitMemoryLink): JsonObject {
        val owner = "$collection/${rkeyOf(value)}"
        return encodeRecord(
            EntryLink.serializer(),
            EntryLink(
                entry = refTo(owner, Entry.TYPE, value.noteId),
                evidenceId = value.targetEvidenceId,
                createdAt = value.createdAt?.toDatetime("$owner createdAt"),
            ),
        )
    }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): VisitMemoryLink {
        val owner = "$collection/$rkey"
        val decoded = decodeRecord(EntryLink.serializer(), collection, rkey, record)
        val link =
            VisitMemoryLink(
                noteId = keyOfRef(owner, Entry.TYPE, decoded.entry),
                targetEvidenceId = decoded.evidenceId,
                createdAt = decoded.createdAt?.let { parseDatetime("$owner createdAt", it) },
            )
        if (!sameRecordKey(
                rkey,
                rkeyOf(link),
            )
        ) {
            throw SpaceRecordMappingException("$owner: it isn't stored under the key its entry and visit give it")
        }
        return link
    }

    /** The record key of the link between entry [noteId] and the visit named [evidenceId]. */
    fun keyOf(
        noteId: String,
        evidenceId: String,
    ): String = keyFor("$collection/$noteId", "$noteId:${sha256Hex(evidenceId)}")

    private fun sha256Hex(text: String): String =
        CryptographyProvider.Default
            .get(SHA256)
            .hasher()
            .hashBlocking(text.encodeToByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
