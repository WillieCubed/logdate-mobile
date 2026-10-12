package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchCaptureProtocolTest {
    private var enabled = true
    private val initial = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var current: PeerDeliveryContext? = initial
    private val peers = mockk<WatchDeliveryPeer>()
    private val states = PeerStateStore()
    private val storage = MemoryKeyValueStorage()

    private fun protocol(state: PeerStateStore = states) = WatchCaptureProtocol({ enabled }, peers, state, storage)

    init {
        coEvery { peers.current() } answers { current }
        coEvery { peers.isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == current }
    }

    private fun advertise(
        node: String = "phone",
        features: Set<String> = setOf(WearFeatures.OPERATION_RECEIPTS),
    ) {
        states.accept(
            node,
            Decoded.Supported(StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState(features = features))),
            PeerRole.PHONE,
        )
    }

    @Test
    fun `the linked phone can negotiate operation receipts`() =
        runTest {
            advertise()
            assertTrue(protocol().active())
        }

    @Test
    fun `legacy receipts alone preserve legacy delivery`() =
        runTest {
            advertise(features = setOf(WearFeatures.RECEIPTS))
            assertFalse(protocol().active())
        }

    @Test
    fun `another phone cannot negotiate for this exchange`() =
        runTest {
            advertise("other-phone")
            assertFalse(protocol().active())
        }

    @Test
    fun `negotiation survives process restart without the phone`() =
        runTest {
            advertise()
            assertTrue(protocol().active())
            assertTrue(protocol(PeerStateStore()).active())
        }

    @Test
    fun `cached negotiation cannot authorize a different exchange`() =
        runTest {
            advertise()
            assertTrue(protocol().active())
            current = initial.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertFalse(protocol(PeerStateStore()).active())
        }

    @Test
    fun `losing the verified peer holds negotiated captures instead of using legacy acknowledgments`() =
        runTest {
            advertise()
            assertTrue(protocol().active())
            current = null
            assertTrue(protocol(PeerStateStore()).active())
        }

    @Test
    fun `the rollout switch always disables negotiated delivery`() =
        runTest {
            advertise()
            assertTrue(protocol().active())
            enabled = false
            assertFalse(protocol().active())
        }
}
