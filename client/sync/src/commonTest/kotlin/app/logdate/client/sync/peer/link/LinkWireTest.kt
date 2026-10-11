package app.logdate.client.sync.peer.link

import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests [LinkWire]: answers and refusals read back as what they are, and keys travel as Base64. */
class LinkWireTest {
    @Test
    fun `an accept reads back as an answer`() {
        val accept = LinkAccept(watchNode = "watch", watchPublicKey = "a2V5", watchNonce = "bm9uY2U=")

        assertEquals(
            LinkAnswer.Answered(accept),
            LinkWire.read(LinkWire.write(LinkAccept.serializer(), accept), LinkAccept.serializer()),
        )
    }

    @Test
    fun `a refusal reads back as a refusal whatever answer was expected`() {
        val bytes = LinkWire.refusal(LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT)

        assertEquals(LinkAnswer.Refused(LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT), LinkWire.read(bytes, LinkConfirm.serializer()))
    }

    @Test
    fun `bytes that are not a link message are unreadable`() {
        assertEquals(
            LinkAnswer.Unreadable(LinkExchangeFailure.MALFORMED),
            LinkWire.read("nope".encodeToByteArray(), LinkConfirm.serializer()),
        )
    }

    @Test
    fun `an answer written by a newer version is unsupported`() {
        val bytes = """{"schema":2,"mac":"bWFj"}""".encodeToByteArray()

        assertEquals(LinkAnswer.Unreadable(LinkExchangeFailure.UNSUPPORTED_SCHEMA), LinkWire.read(bytes, LinkConfirm.serializer()))
    }

    @Test
    fun `keys round trip through Base64`() {
        val key = ByteArray(32) { it.toByte() }

        assertContentEquals(key, LinkWire.decodeKey(LinkWire.encodeKey(key)))
    }

    @Test
    fun `text that is not Base64 is no key`() {
        assertNull(LinkWire.decodeKey("not base64!"))
    }
}
