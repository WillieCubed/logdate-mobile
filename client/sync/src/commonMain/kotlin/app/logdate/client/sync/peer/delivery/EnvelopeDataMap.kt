package app.logdate.client.sync.peer.delivery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.MediaRef
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordEnvelopeCodec
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WindowReason
import kotlinx.serialization.json.JsonElement

/**
 * A [RecordEnvelope] as a data item. [KEY_SENT_AT] lets the sender make a resend a changed item; the Data
 * Layer drops a put that matches what it already has.
 */
object EnvelopeDataMap {
    const val KEY_ENVELOPE = "envelope"
    const val KEY_SENT_AT = "sentAt"

    /** Throws [IllegalArgumentException] for an envelope [RecordEnvelopeCodec] refuses to write. */
    fun toDataMap(
        envelope: RecordEnvelope,
        sentAt: Long,
    ): Map<String, String> = mapOf(KEY_ENVELOPE to RecordEnvelopeCodec.encode(envelope), KEY_SENT_AT to sentAt.toString())

    fun fromDataMap(data: Map<String, String>): Decoded<RecordEnvelope> {
        val text = data[KEY_ENVELOPE] ?: return Decoded.Malformed("The data item has no record envelope")
        return RecordEnvelopeCodec.decode(text)
    }
}

/** A note as an envelope body: the same JSON version 1 sends, so both protocols agree on every field. */
object NoteEnvelopeBody {
    fun encode(
        note: JournalNote,
        mapper: NoteDataMapper = NoteDataMapper(),
    ): JsonElement = WearJson.json.parseToJsonElement(checkNotNull(mapper.toDataMap(note)[NoteDataMapper.KEY_JSON_PAYLOAD]))

    /** Throws [IllegalArgumentException] for a body that is not a note. */
    fun decode(
        body: JsonElement,
        mapper: NoteDataMapper = NoteDataMapper(),
    ): JournalNote = mapper.fromDataMap(mapOf(NoteDataMapper.KEY_JSON_PAYLOAD to body.toString()))

    fun envelope(
        note: JournalNote,
        origin: RecordOrigin,
        priority: DeliveryPriority,
        reasons: Set<WindowReason> = emptySet(),
        media: List<MediaRef> = emptyList(),
    ): RecordEnvelope =
        RecordEnvelope(
            kind = RecordKind.NOTE,
            id = note.uid.toString(),
            contentVersion = note.lastUpdated.toEpochMilliseconds(),
            syncVersion = note.syncVersion,
            origin = origin,
            reasons = reasons,
            priority = priority,
            body = encode(note),
            media = media,
        )
}
