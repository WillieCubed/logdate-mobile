package app.logdate.client.sync.peer.delivery

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PeerDeliveryContextTest {
    private val pair = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val cloud = pair.copy(mode = LinkMode.CLOUD, accountId = "account", serverOrigin = "https://logdate.test")

    @Test
    fun `pair and cloud contexts retain their complete provenance on the wire`() {
        for (context in listOf(pair, cloud)) {
            assertTrue(context.isValid())
            val bytes = WearJson.encodeToBytes(PeerDeliveryContext.serializer(), context)
            assertEquals(Decoded.Supported(context), WearJson.decode(bytes, 1, PeerDeliveryContext.serializer()))
        }
    }

    @Test
    fun `an incomplete or inconsistent context is not a delivery authority`() {
        val invalid =
            listOf(
                pair.copy(linkId = ""),
                pair.copy(linkId = "not-base64"),
                pair.copy(linkId = LinkWire.encodeKey(ByteArray(31))),
                pair.copy(phoneNode = ""),
                pair.copy(watchNode = " "),
                pair.copy(watchNode = "phone"),
                pair.copy(accountId = "account"),
                pair.copy(serverOrigin = "https://logdate.test"),
                cloud.copy(accountId = null),
                cloud.copy(accountId = " "),
                cloud.copy(serverOrigin = null),
                cloud.copy(serverOrigin = ""),
            )
        invalid.forEach { assertFalse(it.isValid()) }
    }

    @Test
    fun `fresh links have different delivery namespaces`() {
        assertNotEquals(pair.ledgerScope, pair.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 })).ledgerScope)
        assertEquals(pair.ledgerScope, cloud.ledgerScope)
    }
}
