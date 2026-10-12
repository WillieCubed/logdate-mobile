package app.logdate.client.sync.datalayer.v2

import app.logdate.client.sync.peer.link.LinkWire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

class WindowMessagesTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val id = Uuid.parse("00000000-0000-0000-0000-000000000001")

    @Test
    fun `resync messages preserve the exact link audience`() {
        val request = WindowResyncRequest(context = peer)
        val response =
            WindowResyncResponse(context = peer, members = listOf(WindowMemberRef(RecordKind.NOTE, id.toString(), 5)), publishedAt = 9)
        assertEquals(
            Decoded.Supported(request),
            WearJson.decode(
                WearJson.encode(WindowResyncRequest.serializer(), request),
                WearProtocol.WINDOW_SCHEMA,
                WindowResyncRequest.serializer(),
            ),
        )
        assertEquals(
            Decoded.Supported(response),
            WearJson.decode(
                WearJson.encode(WindowResyncResponse.serializer(), response),
                WearProtocol.WINDOW_SCHEMA,
                WindowResyncResponse.serializer(),
            ),
        )
    }

    @Test
    fun `an unbound resync request cannot be read as a linked request`() {
        assertIs<Decoded.Malformed>(WearJson.decode("""{"schema":1}""", 1, WindowResyncRequest.serializer()))
    }

    @Test
    fun `a resync answer from a newer phone is unsupported`() {
        assertIs<Decoded.Unsupported>(
            WearJson.decode("""{"schema":3,"members":[],"publishedAt":1}""", WearProtocol.WINDOW_SCHEMA, WindowResyncResponse.serializer()),
        )
    }

    @Test
    fun `window items and removals use separate link scoped paths`() {
        val segment = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE"
        val item = WearPaths.windowItem(RecordKind.NOTE, id, peer)
        val removal = WearPaths.windowTombstone(RecordKind.NOTE, id, peer)
        assertEquals("/logdate/v2/p2w/window/note/$id/$segment", item)
        assertEquals("/logdate/v2/p2w/tombstone/note/$id/$segment", removal)
        assertEquals(RecordPath(RecordKind.NOTE, id, peer.linkId), WearPaths.parseWindowItem(item))
        assertEquals(RecordPath(RecordKind.NOTE, id, peer.linkId), WearPaths.parseWindowTombstone(removal))
        val other = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
        assertNotEquals(item, WearPaths.windowItem(RecordKind.NOTE, id, other))
    }

    @Test
    fun `a removal carries both its timestamp and exact audience`() {
        assertEquals(
            mapOf("removedAt" to "7", "context" to WearJson.encode(PeerDeliveryContext.serializer(), peer), "windowRevision" to "1"),
            WindowTombstoneDataMap.toDataMap(7, peer, 1),
        )
    }

    @Test
    fun `ordered window messages use schema two`() {
        assertEquals(2, WearProtocol.WINDOW_SCHEMA)
    }
}
