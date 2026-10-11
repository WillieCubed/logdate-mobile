package app.logdate.wear.sync.v2.link

import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.link.LinkAnswer
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.WatchLinkExchange
import app.logdate.client.sync.peer.link.valueOrNull
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.MemoryPairKeyVault
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [WatchLinkCoordinator] against the phone's half of the exchange: a pairing is stored with its
 * key, every refusal names a reason the phone can act on, and a move to another phone marks it stale.
 */
class WatchLinkCoordinatorTest {
    private val clock = MutableClock(1_000_000)
    private val handshake = BouncyCastleLinkHandshake()
    private val phone = PhoneLinkExchange(handshake, clock)
    private val pairKeys = MemoryPairKeyVault()
    private val store = WatchLinkStore(MemoryKeyValueStorage(), pairKeys, clock)
    private var enabled = true
    private val coordinator =
        WatchLinkCoordinator(
            exchange = WatchLinkExchange(handshake, LinkNonceLedger(clock), clock),
            store = store,
            v2Enabled = { enabled },
            localNodeId = { WATCH },
        )

    @Test
    fun `an offer and its bundle pair the watch with the phone`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
            val sealed = assertNotNull(phone.seal(started, accept, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())

            val confirm = answered(coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed)), LinkConfirm.serializer())

            assertTrue(phone.verify(sealed, confirm))
            assertEquals(PeerLinkState.PAIRED, store.current().peerLinkState())
            assertContentEquals(sealed.pairKey, pairKeys.keys[PHONE])
        }

    @Test
    fun `with the redesigned app off the watch refuses an offer`() =
        runTest {
            enabled = false

            val answer = coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(phone.start(LinkMode.PAIR, PHONE)))

            assertEquals(LinkAnswer.Refused(LinkRefusal.UNSUPPORTED), LinkWire.read(assertNotNull(answer), LinkAccept.serializer()))
            assertNull(store.current())
        }

    @Test
    fun `a bundle with no open offer is refused as expired`() =
        runTest {
            val bundle = SealedLinkBundle(iv = "aXY=", ciphertext = "Y2lwaGVy")

            val answer = coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, LinkWire.write(SealedLinkBundle.serializer(), bundle))

            assertEquals(LinkAnswer.Refused(LinkRefusal.EXPIRED_OFFER), LinkWire.read(assertNotNull(answer), LinkConfirm.serializer()))
        }

    @Test
    fun `a bundle from another phone than the offer's is refused`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
            val sealed = assertNotNull(phone.seal(started, accept, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())

            val answer = coordinator.answer("another-phone", WearPaths.LINK_BUNDLE, bundleBytes(sealed))

            assertEquals(LinkAnswer.Refused(LinkRefusal.EXPIRED_OFFER), LinkWire.read(assertNotNull(answer), LinkConfirm.serializer()))
            assertNull(store.current())
        }

    @Test
    fun `an account link is refused until this watch can install one`() =
        runTest {
            val answer = coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(phone.start(LinkMode.CLOUD, PHONE)))

            assertEquals(LinkAnswer.Refused(LinkRefusal.UNSUPPORTED), LinkWire.read(assertNotNull(answer), LinkAccept.serializer()))
        }

    @Test
    fun `an offer that is not a link offer is refused`() =
        runTest {
            val answer = coordinator.answer(PHONE, WearPaths.LINK_OFFER, "{}".encodeToByteArray())

            assertEquals(LinkAnswer.Refused(LinkRefusal.UNSUPPORTED), LinkWire.read(assertNotNull(answer), LinkAccept.serializer()))
        }

    @Test
    fun `moving to another phone marks the pairing stale`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
            val sealed = assertNotNull(phone.seal(started, accept, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed))

            coordinator.onNodeMigrated(PHONE)

            assertEquals(PeerLinkState.NEEDS_RELINK, store.current().peerLinkState())
        }

    @Test
    fun `a path that is not a link request has no answer`() =
        runTest {
            assertNull(coordinator.answer(PHONE, "/logdate/v2/receipts", ByteArray(0)))
            assertTrue(WatchLinkCoordinator.handles(WearPaths.LINK_OFFER))
        }

    @Test
    fun `turning the redesign off between offer and bundle cannot pair`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
            val sealed = assertNotNull(phone.seal(started, accept, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            enabled = false
            val response = coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed))
            assertEquals(LinkAnswer.Refused(LinkRefusal.UNSUPPORTED), LinkWire.read(assertNotNull(response), LinkConfirm.serializer()))
            assertNull(store.current())
            enabled = true
            val replay = coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed))
            assertEquals(LinkAnswer.Refused(LinkRefusal.EXPIRED_OFFER), LinkWire.read(assertNotNull(replay), LinkConfirm.serializer()))
        }

    @Test
    fun `moving to another phone invalidates an open offer`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
            val sealed = assertNotNull(phone.seal(started, accept, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            coordinator.onNodeMigrated(PHONE)
            val response = coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed))
            assertEquals(LinkAnswer.Refused(LinkRefusal.EXPIRED_OFFER), LinkWire.read(assertNotNull(response), LinkConfirm.serializer()))
            assertNull(store.current())
        }

    private fun offerBytes(started: PhoneLinkExchange.Started): ByteArray = LinkWire.write(LinkOffer.serializer(), started.offer)

    private fun bundleBytes(sealed: PhoneLinkExchange.Sealed): ByteArray = LinkWire.write(SealedLinkBundle.serializer(), sealed.bundle)

    private fun <T> answered(
        bytes: ByteArray?,
        serializer: KSerializer<T>,
    ): T {
        val answer = LinkWire.read(assertNotNull(bytes), serializer)
        if (answer !is LinkAnswer.Answered) fail("Expected an answer but the watch sent $answer")
        return answer.value
    }

    private class MutableClock(
        var millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val PHONE = "phone-node"
        const val WATCH = "watch-node"
    }
}
