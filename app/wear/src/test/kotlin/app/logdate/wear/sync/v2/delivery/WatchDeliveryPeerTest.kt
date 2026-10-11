package app.logdate.wear.sync.v2.delivery

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkProblem
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.MemoryPairKeyVault
import app.logdate.wear.sync.v2.link.WatchLinkStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WatchDeliveryPeerTest {
    private val links = WatchLinkStore(MemoryKeyValueStorage(), MemoryPairKeyVault())
    private val sessions = mockk<SessionStorage>()
    private val origin = "https://logdate.test"
    private val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
    private var bound: OriginBoundSession? = OriginBoundSession(origin, UserSession("a", "r", "account"))
    private var raw: UserSession? = bound!!.session
    private var enabled = true
    private var node: String? = "watch"
    private var onReadNode: suspend () -> Unit = {}
    private val linkId = LinkWire.encodeKey(ByteArray(32) { 1 })
    private val expected = PeerDeliveryContext(linkId, "phone", "watch", LinkMode.CLOUD, "account", origin)
    private val peer =
        WatchDeliveryPeer(links, sessions, config, {
            onReadNode()
            node
        }, { enabled })

    init {
        every { sessions.getOriginBoundSession() } answers { bound }
        every { sessions.getSession() } answers { raw }
    }

    @Test
    fun `installed account resolves the exact exchange and both node IDs`() =
        runTest {
            link()
            assertEquals(expected, peer.current())
        }

    @Test
    fun `pair-only delivery requires no installed or opaque account session`() =
        runTest {
            links.savePairing("phone", ByteArray(32), linkId)
            assertNull(peer.current())
            bound = null
            assertNull(peer.current())
            raw = null
            assertEquals(expected.copy(mode = LinkMode.PAIR, accountId = null, serverOrigin = null), peer.current())
        }

    @Test
    fun `legacy and malformed exchange identities wait for relinking`() =
        runTest {
            links.saveCloudLink("phone", "account", origin)
            assertNull(peer.current())
            links.saveCloudLink("phone", "account", origin, "bad-id")
            assertNull(peer.current())
        }

    @Test
    fun `a stale phone cannot be used for delivery`() =
        runTest {
            link()
            links.markStale("phone")
            assertNull(peer.current())
        }

    @Test
    fun `incomplete account installation cannot deliver`() =
        runTest {
            links.beginCloudInstall("phone", ByteArray(32), "account", origin, linkId)
            assertNull(peer.current())
        }

    @Test
    fun `a failed link cannot deliver`() =
        runTest {
            link()
            links.recordProblem("phone", LinkProblem.SESSION_REVOKED)
            assertNull(peer.current())
        }

    @Test
    fun `missing local node prevents a fabricated watch audience`() =
        runTest {
            link()
            node = null
            assertNull(peer.current())
        }

    @Test
    fun `a different installed account cannot use this link`() =
        runTest {
            link()
            bound = OriginBoundSession(origin, UserSession("a", "r", "another-account"))
            raw = bound!!.session
            assertNull(peer.current())
        }

    @Test
    fun `a different backend cannot use this link`() =
        runTest {
            link()
            config.updateBackendUrl("https://another.test")
            assertNull(peer.current())
        }

    @Test
    fun `an unbound session cannot authorize cloud delivery`() =
        runTest {
            link()
            bound = null
            assertNull(peer.current())
        }

    @Test
    fun `signout during local node lookup discards the captured audience`() =
        runTest {
            link()
            onReadNode = {
                bound = null
                raw = null
            }
            assertNull(peer.current())
        }

    @Test
    fun `gate off during lookup discards the audience`() =
        runTest {
            link()
            onReadNode = { enabled = false }
            assertNull(peer.current())
        }

    @Test
    fun `a renewed exchange during lookup discards the old audience`() =
        runTest {
            link()
            onReadNode = { links.saveCloudLink("phone", "account", origin, LinkWire.encodeKey(ByteArray(32) { 2 })) }
            assertNull(peer.current())
        }

    @Test
    fun `an audience for a different local node is not current`() =
        runTest {
            link()
            assertFalse(peer.isCurrent(expected.copy(watchNode = "another-watch")))
        }

    @Test
    fun `unreadable session state waits without authorizing delivery`() =
        runTest {
            link()
            every { sessions.getOriginBoundSession() } throws IllegalStateException("unreadable")
            assertNull(peer.current())
        }

    @Test
    fun `cancellation is propagated instead of converted to missing link`() =
        runTest {
            link()
            onReadNode = { throw CancellationException("stopped") }
            assertFailsWith<CancellationException> { peer.current() }
        }

    private suspend fun link() = links.saveCloudLink("phone", "account", origin, linkId)
}
