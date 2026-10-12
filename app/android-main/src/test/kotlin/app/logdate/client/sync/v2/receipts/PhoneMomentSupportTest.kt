package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.WatchState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneMomentSupportTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val states = PeerStateStore()
    private var enabled = true
    private val support = PhoneMomentSupport({ enabled }, states)
    private val features = setOf(WearFeatures.MOMENTS, WearFeatures.OPERATION_RECEIPTS)

    private fun advertise(
        node: String = "watch",
        link: String? = peer.linkId,
        advertised: Set<String> = features,
    ) {
        states.accept(
            node,
            Decoded.Supported(
                StateDocument(
                    role = PeerRole.WATCH,
                    writtenAt = 100,
                    watch = WatchState(features = advertised, linkId = link),
                ),
            ),
            PeerRole.WATCH,
        )
    }

    @Test fun onlyTheExactWatchExchangeMayNegotiateMomentReceipts() =
        runTest {
            advertise(node = "another-watch")
            assertFalse(support.accepts(peer))
            advertise(link = null)
            assertFalse(support.accepts(peer))
            advertise(link = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertFalse(support.accepts(peer))
            advertise()
            assertTrue(support.accepts(peer))
        }

    @Test fun bothFeaturesAndRolloutAreRequired() =
        runTest {
            for (advertised in listOf(emptySet(), setOf(WearFeatures.MOMENTS), setOf(WearFeatures.OPERATION_RECEIPTS))) {
                advertise(advertised = advertised)
                assertFalse(support.accepts(peer))
            }
            advertise()
            assertTrue(support.accepts(peer))
            enabled = false
            assertFalse(support.accepts(peer))
        }
}
