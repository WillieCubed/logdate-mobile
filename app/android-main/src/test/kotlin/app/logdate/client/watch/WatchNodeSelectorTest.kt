package app.logdate.client.watch

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests [WatchNodeSelector], which finds the reachable watches running LogDate.
 */
class WatchNodeSelectorTest {
    private val capabilityClient = mockk<CapabilityClient>()
    private val selector = WatchNodeSelector(capabilityClient)

    @Test
    fun `reachable watches running LogDate come back, directly connected first`() =
        runTest {
            givenWatches(watch("cloud-watch", nearby = false), watch("wrist-watch", nearby = true))

            assertEquals(listOf("wrist-watch", "cloud-watch"), selector.reachableWatches().map { it.id })
        }

    @Test
    fun `no watch running LogDate gives no watches`() =
        runTest {
            givenWatches()

            assertEquals(emptyList(), selector.reachableWatches())
        }

    @Test
    fun `only reachable watches are asked for`() =
        runTest {
            givenWatches(watch("wrist-watch"))

            selector.reachableWatches()

            verify { capabilityClient.getCapability(WatchNodeSelector.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE) }
        }

    private fun givenWatches(vararg watches: Node) {
        val info = mockk<CapabilityInfo> { every { nodes } returns watches.toSet() }
        every { capabilityClient.getCapability(WatchNodeSelector.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE) } returns
            Tasks.forResult(info)
    }

    private fun watch(
        id: String,
        nearby: Boolean = true,
    ): Node =
        mockk {
            every { this@mockk.id } returns id
            every { isNearby } returns nearby
            every { displayName } returns id
        }
}
