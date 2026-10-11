package app.logdate.client.sync.datalayer.v2

import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.link.LinkWire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ScopedCaptureWireTest {
    private val id = Uuid.random()
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { -1 }), "phone", "watch", LinkMode.PAIR)

    @Test
    fun `an envelope carries its verified delivery context through the data map`() {
        val envelope =
            RecordEnvelope(
                kind = RecordKind.NOTE,
                id = id.toString(),
                contentVersion = 5,
                origin = RecordOrigin.WATCH,
                context = context,
            )
        val decoded = EnvelopeDataMap.fromDataMap(EnvelopeDataMap.toDataMap(envelope, sentAt = 1)) as Decoded.Supported
        assertEquals(context, decoded.value.context)
    }

    @Test
    fun `a scoped media channel identifies the exact link without path separators`() {
        val path = WearPaths.recordMedia(RecordKind.NOTE, id, context)
        val parsed = WearPaths.parseRecordMedia(path)
        assertEquals(context.linkId, parsed?.linkId)
        assertEquals(id, parsed?.id)
        assertEquals(RecordKind.NOTE, parsed?.kind)
        assertTrue(path.substringAfterLast('/').matches(Regex("[A-Za-z0-9_-]{43}")))
    }

    @Test
    fun `relinking changes the media channel even for the same note and nodes`() {
        val fresh = context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
        assertNotEquals(WearPaths.recordMedia(RecordKind.NOTE, id, context), WearPaths.recordMedia(RecordKind.NOTE, id, fresh))
    }

    @Test
    fun `invalid link identities cannot form media channels`() {
        for (linkId in listOf("../../escape", "", LinkWire.encodeKey(ByteArray(31)))) {
            assertFailsWith<IllegalArgumentException> {
                WearPaths.recordMedia(RecordKind.NOTE, id, context.copy(linkId = linkId))
            }
        }
    }

    @Test
    fun `noncanonical or malformed media scopes are rejected`() {
        val prefix = "/logdate/v2/w2p/media/note/$id/"
        for (segment in listOf("bad", "../escape", "_".repeat(43), "A".repeat(43) + "=", "A".repeat(43) + "/extra", "")) {
            assertNull(WearPaths.parseRecordMedia(prefix + segment), segment)
        }
    }

    @Test
    fun `old media channels stay unbound and keep their existing path`() {
        val path = WearPaths.recordMedia(RecordKind.NOTE, id)
        assertEquals("/logdate/v2/w2p/media/note/$id", path)
        assertEquals(RecordPath(RecordKind.NOTE, id), WearPaths.parseRecordMedia(path))
    }
}
