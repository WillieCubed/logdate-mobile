package app.logdate.wear.sync.v2.removal

import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.removal.RemovalAction
import app.logdate.client.sync.peer.removal.WatchRemovalReply
import app.logdate.client.sync.peer.removal.WatchRemovalRequest
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchRemovalEndpointTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32)), "phone", "watch", LinkMode.PAIR)
    private var current: PeerDeliveryContext? = peer
    private var recording = false
    private var pending = 2
    private var drains = 0
    private var wipes = 0
    private var marker: String? = null
    private val secure = mockk<SecureStorage>()
    private val lock = WatchRemovalLock(secure)

    init {
        coEvery { secure.getString(WatchRemovalLock.KEY) } answers { marker }
        coEvery { secure.putString(WatchRemovalLock.KEY, any()) } answers { marker = secondArg() }
        coEvery { secure.remove(WatchRemovalLock.KEY) } answers { marker = null }
    }

    private val subject =
        WatchRemovalEndpoint(
            current = { current },
            lock = lock,
            recording = { recording },
            drain = {
                drains++
                pending = 0
            },
            pending = { pending },
            wipe = { wipes++ },
            forcedWipe = { wipes++ },
        )

    @Test
    fun `foreign phone cannot drain or erase this watch`() =
        runTest {
            assertNull(subject.answer("other-phone", request(RemovalAction.WIPE)))
            assertEquals(0, wipes)
        }

    @Test
    fun `old link context cannot erase relinked watch`() =
        runTest {
            current = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertNull(subject.answer("phone", request(RemovalAction.WIPE)))
            assertEquals(0, wipes)
        }

    @Test
    fun `drain waits for actual outbox to empty without erasing`() =
        runTest {
            val reply = reply(RemovalAction.DRAIN)
            assertTrue(reply.completed)
            assertEquals(0, reply.pending)
            assertEquals(1, drains)
            assertEquals(0, wipes)
        }

    @Test
    fun `recording cannot be drained or erased by sync first`() =
        runTest {
            recording = true
            assertFalse(reply(RemovalAction.DRAIN).completed)
            assertEquals(0, drains)
            assertEquals(0, wipes)
        }

    @Test
    fun `approved offline erasure is retained until recording ends`() =
        runTest {
            recording = true
            assertFalse(reply(RemovalAction.WIPE).completed)
            assertEquals(0, wipes)
        }

    @Test
    fun `verified wipe acknowledges only completed erasure`() =
        runTest {
            pending = 0
            assertTrue(reply(RemovalAction.DRAIN).completed)
            assertTrue(reply(RemovalAction.WIPE).completed)
            assertEquals(1, wipes)
        }

    @Test
    fun `wipe without an acknowledged prepare preserves watch data`() =
        runTest {
            pending = 0
            assertFalse(reply(RemovalAction.WIPE).completed)
            assertEquals(0, wipes)
        }

    @Test
    fun `approved offline wipe erases pending captures for the current link`() =
        runTest {
            recording = true
            assertTrue(reply(RemovalAction.REVOKED_WIPE).completed)
            assertEquals(1, wipes)
            assertEquals(0, drains)
            assertEquals("wiping-revoked:${peer.linkId}", lock.state())
        }

    @Test
    fun `approved offline wipe cannot erase a newly linked watch`() =
        runTest {
            current = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 4 }))
            assertNull(subject.answer("phone", request(RemovalAction.REVOKED_WIPE)))
            assertEquals(0, wipes)
        }

    @Test
    fun `new pending capture after drain prevents online erasure`() =
        runTest {
            assertTrue(reply(RemovalAction.DRAIN).completed)
            assertFailsWith<IllegalStateException> { lock.write { pending = 1 } }
            assertEquals(0, pending)

            pending = 1 // An unguarded legacy writer must still be caught by the final outbox check.

            val wipe = reply(RemovalAction.WIPE)

            assertFalse(wipe.completed)
            assertEquals(1, wipe.pending)
            assertEquals(0, wipes)
        }

    @Test
    fun `prepared state survives a lock recreation and only the matching link can wipe`() =
        runTest {
            assertTrue(reply(RemovalAction.DRAIN).completed)
            val restarted = WatchRemovalLock(secure)
            assertTrue(restarted.isPrepared(peer.linkId))
            assertFailsWith<IllegalStateException> { restarted.write { pending = 1 } }
            assertNull(restarted.beginWipe(LinkWire.encodeKey(ByteArray(32) { 3 })) { pending })
            assertEquals(0, wipes)
        }

    @Test
    fun `lost wipe acknowledgment can be retried using the retained removal receipt`() =
        runTest {
            assertTrue(reply(RemovalAction.DRAIN).completed)
            lock.mark()
            lock.finish()
            current = null

            assertTrue(reply(RemovalAction.DRAIN).completed)
            assertTrue(reply(RemovalAction.WIPE).completed)
            assertEquals(0, wipes)
        }

    private fun request(action: RemovalAction) =
        WearJson.encodeToBytes(WatchRemovalRequest.serializer(), WatchRemovalRequest(context = peer, action = action))

    private suspend fun reply(action: RemovalAction): WatchRemovalReply =
        (WearJson.decode(subject.answer("phone", request(action))!!, 1, WatchRemovalReply.serializer()) as Decoded.Supported).value
}
