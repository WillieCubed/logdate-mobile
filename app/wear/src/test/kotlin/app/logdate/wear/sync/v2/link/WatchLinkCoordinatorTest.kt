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
import app.logdate.client.sync.peer.link.InstallOutcome
import app.logdate.client.sync.peer.link.LinkAnswer
import app.logdate.client.sync.peer.link.LinkInstaller
import app.logdate.client.sync.peer.link.LinkProblem
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.WatchLinkExchange
import app.logdate.client.sync.peer.link.valueOrNull
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.MemoryPairKeyVault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
@OptIn(ExperimentalCoroutinesApi::class)
class WatchLinkCoordinatorTest {
    private val installer = FakeInstaller()

    private fun accountCoordinator(scope: CoroutineScope) =
        WatchLinkCoordinator(
            exchange = WatchLinkExchange(handshake, LinkNonceLedger(clock), clock),
            store = store,
            v2Enabled = { enabled },
            localNodeId = { WATCH },
            installer = installer,
            installScope = scope,
        )

    @Test
    fun `an account bundle is confirmed and then installed`() =
        runTest {
            val answer = linkAccount(accountCoordinator(backgroundScope))
            runCurrent()

            assertTrue(answer is LinkAnswer.Answered)
            assertEquals(PeerLinkState.LINKED, store.current().peerLinkState())
            assertEquals(ACCOUNT, store.current()?.accountId)
            assertEquals("https://linked.test", store.current()?.serverOrigin)
            assertEquals(1, installer.installs)
        }

    @Test
    fun `a watch that has another account refuses the bundle`() =
        runTest {
            installer.problem = LinkProblem.BOUND_TO_ANOTHER_ACCOUNT

            val answer = linkAccount(accountCoordinator(backgroundScope))
            runCurrent()

            assertEquals(LinkAnswer.Refused(LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT), answer)
            assertEquals(0, installer.installs)
            assertNull(store.current())
        }

    @Test
    fun `a failed install is recorded with its problem`() =
        runTest {
            installer.outcome = InstallOutcome.Failed(LinkProblem.DEVICE_LIMIT_REACHED)

            linkAccount(accountCoordinator(backgroundScope))
            runCurrent()

            assertEquals(PeerLinkState.NEEDS_RELINK, store.current().peerLinkState())
            assertEquals("DEVICE_LIMIT_REACHED", store.current()?.problem)
        }

    @Test
    fun `an install that throws is recorded as interrupted`() =
        runTest {
            installer.throws = true

            linkAccount(accountCoordinator(backgroundScope))
            runCurrent()

            assertEquals("INTERRUPTED", store.current()?.problem)
        }

    @Test
    fun `an install finished on start is recorded`() =
        runTest {
            installer.resumeOutcome = InstallOutcome.Installed(ACCOUNT, "https://linked.test", leftBehind = 0)

            accountCoordinator(backgroundScope).resumeInstall()

            assertEquals(PeerLinkState.LINKED, store.current().peerLinkState())
            assertEquals("https://linked.test", store.current()?.serverOrigin)
        }

    @Test
    fun `cloud confirmation persists its phone while installation is still pending`() =
        runTest {
            val wait = CompletableDeferred<Unit>()
            installer.beforeInstall = { wait.await() }
            assertTrue(linkAccount(accountCoordinator(backgroundScope)) is LinkAnswer.Answered)
            runCurrent()
            assertEquals(PHONE, store.current()?.phoneNode)
            assertEquals(PeerLinkState.PAIRED, store.current().peerLinkState())
            assertEquals("https://linked.test", store.current()?.serverOrigin)
            installer.resumeOutcome = installer.outcome
            accountCoordinator(backgroundScope).resumeInstall()
            assertEquals(PHONE, store.current()?.phoneNode)
            assertEquals(PeerLinkState.LINKED, store.current().peerLinkState())
        }

    @Test
    fun `phone migration while installing remains stale when installation finishes`() =
        runTest {
            val wait = CompletableDeferred<Unit>()
            installer.beforeInstall = { wait.await() }
            val linking = accountCoordinator(backgroundScope)
            linkAccount(linking)
            runCurrent()
            linking.onNodeMigrated(PHONE)
            wait.complete(Unit)
            runCurrent()
            assertEquals(PHONE, store.current()?.phoneNode)
            assertEquals(true, store.current()?.stale)
            assertEquals(PeerLinkState.NEEDS_RELINK, store.current().peerLinkState())
            val next =
                linking.answer(
                    "new-phone",
                    WearPaths.LINK_OFFER,
                    offerBytes(phone.start(LinkMode.CLOUD, "new-phone")),
                )
            assertTrue(LinkWire.read(assertNotNull(next), LinkAccept.serializer()) is LinkAnswer.Answered)
        }

    @Test
    fun `another exchange waits until account installation finishes`() =
        runTest {
            val wait = CompletableDeferred<Unit>()
            installer.beforeInstall = { wait.await() }
            val linking = accountCoordinator(backgroundScope)
            linkAccount(linking)
            runCurrent()
            val answer = linking.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(phone.start(LinkMode.PAIR, PHONE)))
            assertEquals(LinkAnswer.Refused(LinkRefusal.UNSUPPORTED), LinkWire.read(assertNotNull(answer), LinkAccept.serializer()))
            wait.complete(Unit)
            runCurrent()
            val next = linking.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(phone.start(LinkMode.CLOUD, PHONE)))
            assertTrue(LinkWire.read(assertNotNull(next), LinkAccept.serializer()) is LinkAnswer.Answered)
        }

    @Test
    fun `turning off during account preflight refuses confirmation and installation`() =
        runTest {
            val wait = CompletableDeferred<Unit>()
            installer.beforeBinding = { wait.await() }
            val linking = accountCoordinator(backgroundScope)
            var answer: LinkAnswer<LinkConfirm>? = null
            val job = launch { answer = linkAccount(linking) }
            runCurrent()
            enabled = false
            wait.complete(Unit)
            job.join()
            runCurrent()
            assertEquals(LinkAnswer.Refused(LinkRefusal.UNSUPPORTED), answer)
            assertEquals(0, installer.installs)
            assertNull(pairKeys.keys[PHONE])
        }

    @Test
    fun `a resume exception is published as interrupted`() =
        runTest {
            installer.resumeThrows = true
            accountCoordinator(backgroundScope).resumeInstall()
            assertEquals("INTERRUPTED", store.current()?.problem)
        }

    @Test
    fun `turning off after confirmation does not abandon account installation`() =
        runTest {
            val wait = CompletableDeferred<Unit>()
            installer.beforeInstall = { wait.await() }
            linkAccount(accountCoordinator(backgroundScope))
            runCurrent()
            enabled = false
            wait.complete(Unit)
            runCurrent()
            assertEquals(PeerLinkState.LINKED, store.current().peerLinkState())
        }

    @Test
    fun `pending confirmation without an install journal requests relink on restart`() =
        runTest {
            linkAccount(accountCoordinator(backgroundScope))
            accountCoordinator(backgroundScope).resumeInstall()
            assertEquals("INTERRUPTED", store.current()?.problem)
            assertEquals(PHONE, store.current()?.phoneNode)
            assertEquals(PeerLinkState.NEEDS_RELINK, store.current().peerLinkState())
        }

    @Test
    fun `an account linked watch refuses pair only offers without changing its binding`() =
        runTest {
            store.savePairKey(PHONE, byteArrayOf(1, 2, 3))
            store.saveCloudLink(PHONE, ACCOUNT)
            val original = store.current()
            for (node in listOf(PHONE, "another-phone")) {
                val response = coordinator.answer(node, WearPaths.LINK_OFFER, offerBytes(phone.start(LinkMode.PAIR, node)))
                assertEquals(
                    LinkAnswer.Refused(LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT),
                    LinkWire.read(assertNotNull(response), LinkAccept.serializer()),
                )
                assertEquals(original, store.current())
                assertContentEquals(byteArrayOf(1, 2, 3), pairKeys.keys[PHONE])
                assertNull(pairKeys.keys["another-phone"])
            }
            coordinator.onNodeMigrated(PHONE)
            val response = coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(phone.start(LinkMode.PAIR, PHONE)))
            assertEquals(
                LinkAnswer.Refused(LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT),
                LinkWire.read(assertNotNull(response), LinkAccept.serializer()),
            )
            assertEquals(ACCOUNT, store.current()?.accountId)
            assertEquals(true, store.current()?.stale)
        }

    @Test
    fun `a cloud binding established after a pair offer refuses its bundle`() =
        runTest {
            val started = phone.start(LinkMode.PAIR, PHONE)
            val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
            val sealed = assertNotNull(phone.seal(started, accept, WATCH, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
            store.savePairKey(PHONE, byteArrayOf(1, 2, 3))
            store.saveCloudLink(PHONE, ACCOUNT)
            val original = store.current()
            val response = coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed))
            assertEquals(
                LinkAnswer.Refused(LinkRefusal.BOUND_TO_ANOTHER_ACCOUNT),
                LinkWire.read(assertNotNull(response), LinkConfirm.serializer()),
            )
            assertEquals(original, store.current())
            assertContentEquals(byteArrayOf(1, 2, 3), pairKeys.keys[PHONE])
        }

    private suspend fun linkAccount(coordinator: WatchLinkCoordinator): LinkAnswer<LinkConfirm> {
        val started = phone.start(LinkMode.CLOUD, PHONE)
        val accept = answered(coordinator.answer(PHONE, WearPaths.LINK_OFFER, offerBytes(started)), LinkAccept.serializer())
        val bundle = LinkBundle(mode = LinkMode.CLOUD, accountId = ACCOUNT, serverOrigin = "https://linked.test")
        val sealed = assertNotNull(phone.seal(started, accept, WATCH, bundle).valueOrNull())
        return LinkWire.read(assertNotNull(coordinator.answer(PHONE, WearPaths.LINK_BUNDLE, bundleBytes(sealed))), LinkConfirm.serializer())
    }

    private class FakeInstaller : LinkInstaller {
        var problem: LinkProblem? = null
        var outcome: InstallOutcome = InstallOutcome.Installed(ACCOUNT, "https://linked.test", leftBehind = 0)
        var resumeOutcome: InstallOutcome? = null
        var throws = false
        var installs = 0
        var beforeBinding: suspend () -> Unit = {}
        var beforeInstall: suspend () -> Unit = {}
        var resumeThrows = false

        override suspend fun bindingProblem(bundle: LinkBundle): LinkProblem? {
            beforeBinding()
            return problem
        }

        override suspend fun install(bundle: LinkBundle): InstallOutcome {
            installs++
            beforeInstall()
            check(!throws) { "The journal could not be written" }
            return outcome
        }

        override suspend fun resume(): InstallOutcome? {
            check(!resumeThrows) { "journal unavailable" }
            return resumeOutcome
        }
    }

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
        const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"
        const val PHONE = "phone-node"
        const val WATCH = "watch-node"
    }
}
