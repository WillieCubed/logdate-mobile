package app.logdate.wear.sync.v2.delivery

import app.logdate.client.datastore.KeyValueStorage
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

class WatchMomentProtocolTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var current = peer
    private var enabled = true
    private val states = PeerStateStore()
    private val storage = MemoryKeyValueStorage()
    private val peers = mockk<WatchDeliveryPeer>()

    init {
        coEvery { peers.isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == current }
    }

    private fun protocol(state: PeerStateStore = states) = WatchMomentProtocol({ enabled }, peers, state, storage)

    private fun advertise(
        node: String = "phone",
        features: Set<String> = setOf(WearFeatures.MOMENTS, WearFeatures.OPERATION_RECEIPTS),
    ) {
        states.accept(
            node,
            Decoded.Supported(StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState(features = features))),
            PeerRole.PHONE,
        )
    }

    @Test fun negotiatedMomentsSurviveRestartWithoutPhoneButNotRelinkOrFlagOff() =
        runTest {
            advertise()
            assertTrue(protocol().accepts(peer))
            assertTrue(protocol(PeerStateStore()).accepts(peer))
            enabled = false
            assertFalse(protocol(PeerStateStore()).accepts(peer))
            enabled = true
            current = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertFalse(protocol(PeerStateStore()).accepts(current))
            assertFalse(protocol(PeerStateStore()).accepts(peer))
        }

    @Test fun explicitCapabilityRemovalInvalidatesEarlierNegotiation() =
        runTest {
            advertise()
            assertTrue(protocol().accepts(peer))
            advertise(features = setOf(WearFeatures.OPERATION_RECEIPTS))
            assertFalse(protocol().accepts(peer))
            assertFalse(protocol(PeerStateStore()).accepts(peer))
        }

    @Test fun neitherAnotherPhoneNorMomentsWithoutOperationReceiptsGrantsSupport() =
        runTest {
            advertise(node = "another-phone")
            assertFalse(protocol().accepts(peer))
            advertise(features = setOf(WearFeatures.MOMENTS))
            assertFalse(protocol().accepts(peer))
            advertise()
            assertTrue(protocol().accepts(peer))
        }

    @Test fun capabilityRemovalDuringCacheWriteCannotLeaveAnOfflineGrant() =
        runTest {
            advertise()
            val delayed =
                object : KeyValueStorage by storage {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        storage.putString(key, value)
                        advertise(features = setOf(WearFeatures.OPERATION_RECEIPTS))
                    }
                }
            assertFalse(WatchMomentProtocol({ enabled }, peers, states, delayed).accepts(peer))
            assertFalse(protocol(PeerStateStore()).accepts(peer))
        }
}
