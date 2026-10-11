package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.MemoryPairKeyVault
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneWatchLinkStoreTest {
    private val store = PhoneWatchLinkStore(MemoryKeyValueStorage(), MemoryPairKeyVault())
    private val target = LinkTarget("watch", "Watch")
    private val pair = LinkPlan(LinkMode.PAIR)
    private val cloud = LinkPlan(LinkMode.CLOUD, "new-account")

    @Test
    fun `another server for the same account starts its own confirmation and retry history`() =
        runTest {
            val first = cloud.copy(serverOrigin = "https://first.test")
            val second = cloud.copy(serverOrigin = "https://second.test")
            store.recordSuccess(target, first, ByteArray(32), 100, 100)
            store.markConfirmed(target.nodeId, 100, 100)
            val changed = store.recordFailure(target, second, PhoneLinkFailure.WATCH_UNREACHABLE, 200, 200)
            assertNull(changed.confirmedAt)
            assertNull(changed.linkedAt)
            assertEquals(second.serverOrigin, changed.serverOrigin)
            val linked = store.recordSuccess(target, second, ByteArray(32), 300, 300)
            assertEquals(second.serverOrigin, linked.serverOrigin)
        }

    @Test
    fun `a failed new plan cannot inherit confirmation from the previous plan`() =
        runTest {
            store.recordSuccess(target, pair, ByteArray(32), 100, 100)
            store.markConfirmed(target.nodeId, 100, 100)
            val failure = store.recordFailure(target, cloud, PhoneLinkFailure.WATCH_UNREACHABLE, 200, 200)
            assertNull(failure.confirmedAt)
            assertNull(failure.linkedAt)
            val linked = store.recordSuccess(target, cloud, ByteArray(32), 300, 300)
            assertNull(linked.confirmedAt)
        }

    @Test
    fun `a new plan starts its own retry wait`() =
        runTest {
            store.recordFailure(target, pair, PhoneLinkFailure.WATCH_UNREACHABLE, 100, 100)
            val failure = store.recordFailure(target, cloud, PhoneLinkFailure.WATCH_UNREACHABLE, 200, 200)
            assertEquals(1, failure.attempts)
            assertEquals(60_200L, failure.nextAttemptAt)
        }
}
