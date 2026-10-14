package app.logdate.client.sync.peer.removal

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchRemovalCoordinatorTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32)), "phone", "watch", LinkMode.PAIR)
    private val target = WatchRemovalTarget(peer, "My watch", 2)
    private val calls = mutableListOf<String>()
    private var pending = 0
    private var reachable = true
    private var revoked = true
    private var queued = true
    private var pendingAfterDrain = 0
    private var wipeReachable = true
    private val subject =
        WatchRemovalCoordinator(
            current = { it == peer },
            request = { request ->
                calls += request.action.name
                if (reachable && (request.action != RemovalAction.WIPE || wipeReachable)) {
                    val reportedPending = if (request.action == RemovalAction.WIPE) pendingAfterDrain else pending
                    WatchRemovalReply(
                        context = peer,
                        action = request.action,
                        pending = reportedPending,
                        completed = reportedPending == 0,
                    )
                } else {
                    null
                }
            },
            deferWipe = {
                calls += "queue wipe"
                queued
            },
            revoke = {
                calls += "revoke"
                revoked
            },
            disassociate = {
                calls += "disassociate"
                true
            },
            forget = {
                calls += "forget"
                true
            },
        )

    @Test
    fun `reachable watch drains before wiping and only then clears phone link`() =
        runTest {
            assertEquals(WatchRemovalResult.Removed, subject.remove(target))
            assertEquals(listOf("DRAIN", "revoke", "WIPE", "disassociate", "forget"), calls)
        }

    @Test
    fun `pending captures prevent wiping and revocation`() =
        runTest {
            pending = 2
            assertEquals(WatchRemovalResult.Failed("sync"), subject.remove(target))
            assertEquals(listOf("DRAIN"), calls)
        }

    @Test
    fun `offline watch has durable wipe queued before local link is forgotten`() =
        runTest {
            reachable = false
            assertEquals(WatchRemovalResult.RemovedWillWipeOnNextConnect, subject.remove(target))
            assertEquals(listOf("DRAIN", "revoke", "queue wipe", "forget"), calls)
        }

    @Test
    fun `lost wipe reply queues erasure after revocation`() =
        runTest {
            wipeReachable = false
            assertEquals(WatchRemovalResult.RemovedWillWipeOnNextConnect, subject.remove(target))
            assertEquals(listOf("DRAIN", "revoke", "WIPE", "queue wipe", "forget"), calls)
        }

    @Test
    fun `failed deferred queue retains the link for a retry after revocation`() =
        runTest {
            reachable = false
            queued = false
            assertEquals(WatchRemovalResult.Failed("queue wipe"), subject.remove(target))
            assertEquals(listOf("DRAIN", "revoke", "queue wipe"), calls)
        }

    @Test
    fun `failed revocation preserves link and does not erase watch`() =
        runTest {
            revoked = false
            assertEquals(WatchRemovalResult.Failed("revoke"), subject.remove(target))
            assertEquals(listOf("DRAIN", "revoke"), calls)
        }

    @Test
    fun `newly pending capture stops removal without deferring a reachable wipe`() =
        runTest {
            pendingAfterDrain = 1
            assertEquals(WatchRemovalResult.Failed("sync"), subject.remove(target))
            assertEquals(listOf("DRAIN", "revoke", "WIPE"), calls)
        }
}
