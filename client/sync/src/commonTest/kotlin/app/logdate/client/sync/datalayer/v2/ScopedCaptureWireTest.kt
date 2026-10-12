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
    fun `operation record paths keep cleanup separate from a later mutation`() {
        val path = WearPaths.watchRecord(RecordKind.NOTE, id, "operation")
        assertEquals("/logdate/v2/w2p/record/note/$id/op/operation", path)
        assertEquals(RecordPath(RecordKind.NOTE, id, operationId = "operation"), WearPaths.parseWatchRecord(path))
        assertNotEquals(path, WearPaths.watchRecord(RecordKind.NOTE, id, "later-operation"))
    }

    @Test
    fun `malformed operation record paths stay rejected`() {
        for (operation in listOf("", "../escape", "a".repeat(129), "%2Fescape")) {
            assertFailsWith<IllegalArgumentException> { WearPaths.watchRecord(RecordKind.NOTE, id, operation) }
            assertNull(WearPaths.parseWatchRecord("/logdate/v2/w2p/record/note/$id/op/$operation"))
        }
    }

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
    fun `operation channels use an explicit namespace and preserve their identity`() {
        val expected = "/logdate/v2/w2p/media/note/$id/${context.linkPathSegment}/op/operation"
        assertEquals(expected, WearPaths.recordMedia(RecordKind.NOTE, id, context, "operation"))
        assertEquals(RecordPath(RecordKind.NOTE, id, context.linkId, "operation"), WearPaths.parseRecordMedia(expected))
    }

    @Test
    fun `invalid operation suffixes cannot become scoped recording channels`() {
        val prefix = "/logdate/v2/w2p/media/note/$id/${context.linkPathSegment}/op/"
        for (operation in listOf("", "../escape", "%2Fescape", "a".repeat(129), "operation/extra")) {
            assertNull(WearPaths.parseRecordMedia(prefix + operation), operation)
            assertFailsWith<IllegalArgumentException> { WearPaths.recordMedia(RecordKind.NOTE, id, context, operation) }
        }
    }

    @Test
    fun `old media channels stay unbound and keep their existing path`() {
        val path = WearPaths.recordMedia(RecordKind.NOTE, id)
        assertEquals("/logdate/v2/w2p/media/note/$id", path)
        assertEquals(RecordPath(RecordKind.NOTE, id), WearPaths.parseRecordMedia(path))
    }
}
