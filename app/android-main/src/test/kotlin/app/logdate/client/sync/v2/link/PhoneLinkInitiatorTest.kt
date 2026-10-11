package app.logdate.client.sync.v2.link

import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.device.crypto.link.LinkHandshake
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkAccept
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkConfirm
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.LinkOffer
import app.logdate.client.sync.datalayer.v2.LinkRefusal
import app.logdate.client.sync.datalayer.v2.SealedLinkBundle
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.WatchLinkExchange
import app.logdate.client.sync.peer.link.valueOrNull
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.WatchState
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.MemoryPairKeyVault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [PhoneLinkInitiator] against the watch's half of the real exchange: a watch is linked once,
 * every failure is recorded with its reason and waits, and only a working link is confirmed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneLinkInitiatorTest {
    private val clock = MutableClock(1_000_000)
    private val handshake = BouncyCastleLinkHandshake()
    private val watch = FakeWatch(handshake, clock)
    private val pairKeys = MemoryPairKeyVault()
    private val store = PhoneWatchLinkStore(MemoryKeyValueStorage(), pairKeys)
    private val notifier = RecordingNotifier()
    private val enabledFlow = MutableStateFlow(true)
    private val capableFlow = MutableStateFlow(true)
    private var bundleSource: LinkBundleSource = PairOnlyBundleSource

    private fun initiator(enabled: Boolean = true) =
        PhoneLinkInitiator(
            v2Enabled = enabledFlow.also { it.value = enabled },
            watchCapable = capableFlow,
            watchDocuments = MutableStateFlow(emptyMap()),
            targets = { listOf(LinkTarget(WATCH, "Pixel Watch 3")) },
            requester = watch,
            exchange = PhoneLinkExchange(handshake, clock),
            store = store,
            notifier = notifier,
            localNodeId = { PHONE },
            bundles = bundleSource,
            clock = clock,
        )

    @Test
    fun `a watch that was never linked is paired once and the phone shows one confirmation`() =
        runTest {
            val initiator = initiator()

            initiator.reconcile(emptyMap())
            initiator.reconcile(emptyMap())

            assertEquals(listOf(WearPaths.LINK_OFFER, WearPaths.LINK_BUNDLE), watch.requests)
            assertEquals(listOf("linked Pixel Watch 3 PAIR"), notifier.events)
            val record = assertNotNull(store.get(WATCH))
            assertNull(record.failure)
            assertNotNull(record.confirmedAt)
            assertContentEquals(assertNotNull(watch.pairKey), pairKeys.keys[WATCH])
        }

    @Test
    fun `a refused offer is recorded with its reason and tried again only after the wait`() =
        runTest {
            watch.refuseOffersWith = LinkRefusal.UNSUPPORTED
            val initiator = initiator()

            initiator.reconcile(emptyMap())
            initiator.reconcile(emptyMap())

            assertEquals(PhoneLinkFailure.WATCH_CANNOT_LINK, store.get(WATCH)?.failure)
            assertEquals(1, watch.requests.size)
            clock.millis += 60_000
            initiator.reconcile(emptyMap())
            assertEquals(2, watch.requests.size)
        }

    @Test
    fun `a watch set up for another account is recorded and shown once`() =
        runTest {
            watch.refuseOffersWith = LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT
            val initiator = initiator()

            initiator.reconcile(emptyMap())
            clock.millis += 60_000
            initiator.reconcile(emptyMap())

            assertEquals(PhoneLinkFailure.WATCH_BOUND_TO_ANOTHER_ACCOUNT, store.get(WATCH)?.failure)
            assertEquals(listOf("attention Pixel Watch 3 WATCH_BOUND_TO_ANOTHER_ACCOUNT"), notifier.events)
        }

    @Test
    fun `an unreachable watch is recorded as unreachable`() =
        runTest {
            watch.reachable = false

            initiator().reconcile(emptyMap())

            assertEquals(PhoneLinkFailure.WATCH_UNREACHABLE, store.get(WATCH)?.failure)
            assertTrue(notifier.events.isEmpty())
        }

    @Test
    fun `a confirmation that does not verify is not counted as linked`() =
        runTest {
            watch.corruptConfirmation = true

            initiator().reconcile(emptyMap())

            assertEquals(PhoneLinkFailure.CONFIRMATION_MISMATCH, store.get(WATCH)?.failure)
            assertNull(pairKeys.keys[WATCH])
            assertTrue(notifier.events.isEmpty())
        }

    @Test
    fun `a watch that reports losing its link is paired again`() =
        runTest {
            val initiator = initiator()
            initiator.reconcile(emptyMap())
            clock.millis += PhoneLinkDecision.RELINK_GRACE_MS

            initiator.reconcile(mapOf(WATCH to unlinkedDocument(at = clock.millis)))

            assertEquals(4, watch.requests.size)
        }

    @Test
    fun `a report written right after the link does not start another exchange`() =
        runTest {
            val initiator = initiator()
            initiator.reconcile(emptyMap())

            initiator.reconcile(mapOf(WATCH to unlinkedDocument(at = clock.millis + 1)))

            assertEquals(2, watch.requests.size)
        }

    @Test
    fun `with the switch off the phone sends nothing`() =
        runTest {
            initiator(enabled = false).start(backgroundScope)
            runCurrent()

            assertTrue(watch.requests.isEmpty())
        }

    @Test
    fun `a quiet watch is retried when its wait expires`() =
        runTest {
            watch.reachable = false
            initiator().start(backgroundScope)
            runCurrent()
            assertEquals(1, watch.requests.size)
            clock.millis += 60_000
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(2, watch.requests.size)
            assertEquals(2, store.get(WATCH)?.attempts)
        }

    @Test
    fun `turning the switch off interrupts an offer already awaiting an answer`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var interrupted = false
            watch.afterOffer = {
                try {
                    release.await()
                } finally {
                    interrupted = true
                }
            }
            initiator().start(backgroundScope)
            runCurrent()
            assertEquals(1, watch.requests.size)
            enabledFlow.value = false
            runCurrent()
            assertTrue(interrupted)
            assertNull(store.get(WATCH))
            assertNull(pairKeys.keys[WATCH])
        }

    @Test
    fun `losing watch capability interrupts an unfinished exchange`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var interrupted = false
            watch.afterOffer = {
                try {
                    release.await()
                } finally {
                    interrupted = true
                }
            }
            initiator().start(backgroundScope)
            runCurrent()
            capableFlow.value = false
            runCurrent()
            assertTrue(interrupted)
            assertEquals(1, watch.requests.size)
            assertNull(store.get(WATCH))
        }

    @Test
    fun `an explicit reconciliation respects the disabled switch`() =
        runTest {
            initiator(enabled = false).reconcile(emptyMap())
            assertTrue(watch.requests.isEmpty())
        }

    @Test
    fun `an overdue unreachable peer does not block another watch retry`() =
        runTest {
            store.recordFailure(
                LinkTarget("offline", "Offline"),
                LinkPlan(LinkMode.PAIR),
                PhoneLinkFailure.WATCH_UNREACHABLE,
                clock.millis - 60_000,
                0,
            )
            watch.reachable = false
            initiator().start(backgroundScope)
            runCurrent()
            assertEquals(1, watch.requests.size)
            clock.millis += 60_000
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(2, watch.requests.size)
        }

    @Test
    fun `cloud confirmation compares watch documents on the watch clock`() =
        runTest {
            bundleSource =
                object : LinkBundleSource {
                    override fun changes(): Flow<Unit> = flowOf(Unit)

                    override suspend fun plan(watchFeatures: Set<String>) = LinkPlan(LinkMode.CLOUD, "account")

                    override suspend fun bundle(plan: LinkPlan) =
                        LinkBundleResult.Ready(LinkBundle(mode = LinkMode.CLOUD, accountId = "account"))
                }
            val initiator = initiator()
            initiator.reconcile(mapOf(WATCH to StateDocument(role = PeerRole.WATCH, writtenAt = 500, watch = WatchState())))
            assertTrue(notifier.events.isEmpty())
            initiator.reconcile(
                mapOf(
                    WATCH to StateDocument(role = PeerRole.WATCH, writtenAt = 501, watch = WatchState(link = PeerLinkState.LINKED)),
                ),
            )
            assertEquals(listOf("linked Pixel Watch 3 CLOUD"), notifier.events)
            assertEquals(501L, store.get(WATCH)?.seenWatchDocAt)
        }

    @Test
    fun `a bundle plan change interrupts an old exchange`() =
        runTest {
            val changes = MutableStateFlow(0)
            bundleSource =
                object : LinkBundleSource {
                    override fun changes(): Flow<Unit> = changes.map { Unit }

                    override suspend fun plan(watchFeatures: Set<String>) = LinkPlan(LinkMode.PAIR)

                    override suspend fun bundle(plan: LinkPlan) = LinkBundleResult.Ready(LinkBundle(mode = LinkMode.PAIR))
                }
            val release = CompletableDeferred<Unit>()
            var interrupted = false
            watch.afterOffer = {
                try {
                    release.await()
                } finally {
                    interrupted = true
                }
            }
            initiator().start(backgroundScope)
            runCurrent()
            changes.value++
            runCurrent()
            assertTrue(interrupted)
            assertNull(store.get(WATCH))
            assertNull(pairKeys.keys[WATCH])
        }

    private fun unlinkedDocument(at: Long) =
        StateDocument(role = PeerRole.WATCH, writtenAt = at, watch = WatchState(link = PeerLinkState.UNLINKED))

    /** The watch's side of the exchange, answering the way the watch app does. */
    private class FakeWatch(
        handshake: LinkHandshake,
        clock: Clock,
    ) : LinkRequester {
        private val exchange = WatchLinkExchange(handshake, LinkNonceLedger(clock), clock)
        private var accepted: WatchLinkExchange.Accepted? = null
        val requests = mutableListOf<String>()
        var reachable = true
        var refuseOffersWith: LinkRefusal? = null
        var corruptConfirmation = false
        var pairKey: ByteArray? = null
        var afterOffer: suspend () -> Unit = {}

        override suspend fun request(
            nodeId: String,
            path: String,
            payload: ByteArray,
        ): Result<ByteArray> {
            requests += path
            if (!reachable) return Result.failure(IOException("The watch is out of reach"))
            return Result.success(
                when (path) {
                    WearPaths.LINK_OFFER -> answerOffer(nodeId, payload).also { afterOffer() }
                    WearPaths.LINK_BUNDLE -> answerBundle(payload)
                    else -> error("Unexpected request $path")
                },
            )
        }

        private suspend fun answerOffer(
            nodeId: String,
            payload: ByteArray,
        ): ByteArray {
            refuseOffersWith?.let { return LinkWire.refusal(it) }
            val offer = (WearJson.decode(payload, WearProtocol.LINK_SCHEMA, LinkOffer.serializer()) as Decoded.Supported).value
            val step = checkNotNull(exchange.accept(offer, watchNode = nodeId, phoneNode = offer.phoneNode).valueOrNull())
            accepted = step
            return LinkWire.write(LinkAccept.serializer(), step.reply)
        }

        private fun answerBundle(payload: ByteArray): ByteArray {
            val sealed = (WearJson.decode(payload, WearProtocol.LINK_SCHEMA, SealedLinkBundle.serializer()) as Decoded.Supported).value
            val opened = checkNotNull(exchange.open(checkNotNull(accepted), sealed).valueOrNull())
            pairKey = opened.pairKey
            val confirm = if (corruptConfirmation) LinkConfirm(mac = LinkWire.encodeKey(ByteArray(32))) else opened.confirm
            return LinkWire.write(LinkConfirm.serializer(), confirm)
        }
    }

    private class RecordingNotifier : WatchLinkNotifier {
        val events = mutableListOf<String>()

        override fun linked(
            target: LinkTarget,
            mode: LinkMode,
        ) {
            events += "linked ${target.displayName} $mode"
        }

        override fun needsAttention(
            target: LinkTarget,
            failure: PhoneLinkFailure,
        ) {
            events += "attention ${target.displayName} $failure"
        }
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
