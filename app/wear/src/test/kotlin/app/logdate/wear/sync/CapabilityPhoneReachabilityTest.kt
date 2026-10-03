package app.logdate.wear.sync

import app.cash.turbine.test
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [CapabilityPhoneReachability], which follows the phone coming into and going out of reach.
 */
class CapabilityPhoneReachabilityTest {
    private val capabilityClient = mockk<CapabilityClient>()
    private val dataLayerClient = mockk<WearDataLayerClient>()
    private val listener = slot<CapabilityClient.OnCapabilityChangedListener>()
    private val reachability = CapabilityPhoneReachability(capabilityClient, dataLayerClient)

    init {
        every { capabilityClient.addListener(capture(listener), WearDataLayerClient.PHONE_CAPABILITY) } returns
            Tasks.forResult<Void>(null)
        every { capabilityClient.removeListener(any(), WearDataLayerClient.PHONE_CAPABILITY) } returns Tasks.forResult(true)
    }

    @Test
    fun `the phone in reach is reported at once`() =
        runTest {
            coEvery { dataLayerClient.getConnectedPhoneName() } returns "Pixel 9"

            reachability.observe().test {
                assertEquals(ReachablePhone("Pixel 9"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the phone leaving and coming back is followed without polling`() =
        runTest {
            coEvery { dataLayerClient.getConnectedPhoneName() } returnsMany listOf("Pixel 9", null, "Pixel 9")

            reachability.observe().test {
                assertEquals(ReachablePhone("Pixel 9"), awaitItem())
                listener.captured.onCapabilityChanged(mockk<CapabilityInfo>())
                assertNull(awaitItem())
                listener.captured.onCapabilityChanged(mockk<CapabilityInfo>())
                assertEquals(ReachablePhone("Pixel 9"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `stopping observation stops listening`() =
        runTest {
            coEvery { dataLayerClient.getConnectedPhoneName() } returns null

            reachability.observe().test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            verify { capabilityClient.removeListener(listener.captured, WearDataLayerClient.PHONE_CAPABILITY) }
        }

    @Test
    fun `reachability is still reported when listening cannot start`() =
        runTest {
            every { capabilityClient.addListener(any(), WearDataLayerClient.PHONE_CAPABILITY) } returns
                Tasks.forException(IllegalStateException("API_UNAVAILABLE"))
            coEvery { dataLayerClient.getConnectedPhoneName() } returns "Pixel 9"

            reachability.observe().test {
                assertEquals(ReachablePhone("Pixel 9"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            verify(exactly = 0) { capabilityClient.removeListener(any(), any<String>()) }
        }
}
