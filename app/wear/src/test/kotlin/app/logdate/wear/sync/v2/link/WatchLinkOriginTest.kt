package app.logdate.wear.sync.v2.link

import app.logdate.client.sync.peer.link.LinkProblem
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.MemoryPairKeyVault
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchLinkOriginTest {
    private val storage = MemoryKeyValueStorage()
    private val keys = MemoryPairKeyVault()
    private val store = WatchLinkStore(storage, keys)

    @Test
    fun `a completed account link retains its origin after restart and migration`() =
        runTest {
            store.saveCloudLink("phone", "account", "https://linked.test", linkId = "exchange")
            val restarted = WatchLinkStore(storage, keys)
            assertEquals("https://linked.test", restarted.current()?.serverOrigin)
            assertEquals("exchange", restarted.current()?.linkId)
            restarted.markStale("phone")
            assertEquals("https://linked.test", restarted.current()?.serverOrigin)
        }

    @Test
    fun `an interrupted install retains the origin it was setting up`() =
        runTest {
            store.beginCloudInstall("phone", ByteArray(32), "account", "https://linked.test", linkId = "exchange")
            assertEquals("https://linked.test", WatchLinkStore(storage, keys).current()?.serverOrigin)
            assertEquals("exchange", store.current()?.linkId)
            store.recordProblem("phone", LinkProblem.INTERRUPTED)
            assertEquals("exchange", store.current()?.linkId)
            assertEquals("https://linked.test", store.current()?.serverOrigin)
        }

    @Test
    fun `an older record has no inferred server provenance`() =
        runTest {
            storage.putString("watch.link.record.v1", """{"mode":"CLOUD","phoneNode":"phone","linkedAt":1,"accountId":"account"}""")
            assertEquals("account", store.current()?.accountId)
            assertNull(store.current()?.serverOrigin)
        }
}
