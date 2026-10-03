package app.logdate.wear.sync

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [PhoneNodeSelector], which picks the phone a watch message or file goes to.
 */
class PhoneNodeSelectorTest {
    private val capabilityClient = mockk<CapabilityClient>()
    private val nodeClient = mockk<NodeClient>()
    private val selector = PhoneNodeSelector(capabilityClient, nodeClient)

    @Test
    fun `a phone advertising LogDate wins over the first connected node`() =
        runTest {
            val other = node("other-node")
            val phone = node("phone-node")
            givenAdvertising(phone)
            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(other, phone))

            assertEquals("phone-node", selector.select()?.id)
        }

    @Test
    fun `a phone connected directly wins over one reached through the cloud`() =
        runTest {
            givenAdvertising(node("cloud-phone", nearby = false), node("nearby-phone", nearby = true))

            assertEquals("nearby-phone", selector.select()?.id)
        }

    @Test
    fun `connected nodes are not consulted when a phone advertises LogDate`() =
        runTest {
            givenAdvertising(node("phone-node"))

            selector.select()

            verify(exactly = 0) { nodeClient.connectedNodes }
        }

    @Test
    fun `a phone build that advertises nothing gets the nearby connected node`() =
        runTest {
            givenAdvertising()
            every { nodeClient.connectedNodes } returns
                Tasks.forResult(listOf(node("cloud-node", nearby = false), node("old-phone", nearby = true)))

            assertEquals("old-phone", selector.select()?.id)
        }

    @Test
    fun `nothing connected selects nothing`() =
        runTest {
            givenAdvertising()
            every { nodeClient.connectedNodes } returns Tasks.forResult(emptyList())

            assertNull(selector.select())
        }

    private fun givenAdvertising(vararg advertising: Node) {
        val info = mockk<CapabilityInfo> { every { nodes } returns advertising.toSet() }
        every {
            capabilityClient.getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        } returns Tasks.forResult(info)
    }

    private fun node(
        id: String,
        nearby: Boolean = true,
    ): Node =
        mockk {
            every { this@mockk.id } returns id
            every { isNearby } returns nearby
            every { displayName } returns id
        }
}
