package app.logdate.client.sync.peer.delivery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [EnvelopeDataMap] and [NoteEnvelopeBody]: a note crosses as the same JSON version 1 sends. */
class EnvelopeDataMapTest {
    private val note =
        JournalNote.Text(
            uid = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
            creationTimestamp = Instant.fromEpochMilliseconds(1_000),
            lastUpdated = Instant.fromEpochMilliseconds(2_000),
            content = "said on the watch",
            syncVersion = 3,
        )

    @Test
    fun `a note envelope names the note and its version`() {
        val envelope = NoteEnvelopeBody.envelope(note, RecordOrigin.WATCH, DeliveryPriority.URGENT)

        assertEquals(RecordKind.NOTE, envelope.kind)
        assertEquals(note.uid.toString(), envelope.id)
        assertEquals(2_000, envelope.contentVersion)
        assertEquals(3, envelope.syncVersion)
    }

    @Test
    fun `a note body reads back as the note`() {
        assertEquals(note, NoteEnvelopeBody.decode(NoteEnvelopeBody.encode(note)))
    }

    @Test
    fun `an envelope round trips through a data map`() {
        val envelope = NoteEnvelopeBody.envelope(note, RecordOrigin.WATCH, DeliveryPriority.URGENT)
        val data = EnvelopeDataMap.toDataMap(envelope, sentAt = 7)

        assertEquals("7", data[EnvelopeDataMap.KEY_SENT_AT])
        assertEquals(Decoded.Supported(envelope), EnvelopeDataMap.fromDataMap(data))
    }

    @Test
    fun `a data map without an envelope is malformed`() {
        assertIs<Decoded.Malformed>(EnvelopeDataMap.fromDataMap(mapOf("other" to "x")))
    }
}
