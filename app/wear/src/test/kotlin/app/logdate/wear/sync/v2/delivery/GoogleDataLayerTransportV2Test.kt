package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.wear.sync.PhoneNodeSelector
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests [GoogleDataLayerTransportV2], the watch's version 2 Data Layer adapter. */
class GoogleDataLayerTransportV2Test {
    private val dataLayer = mockk<WearDataLayerClient>(relaxed = true)
    private val phones = mockk<PhoneNodeSelector>()
    private val messageClient = mockk<MessageClient>()
    private val transport = GoogleDataLayerTransportV2(dataLayer, phones, messageClient)

    @Test
    fun `a phone connected directly is nearby and one reached through the cloud is relay only`() =
        runTest {
            coEvery { phones.select() } returns node("direct", nearby = true)
            assertEquals(PhoneReach.NEARBY, transport.reach())

            coEvery { phones.select() } returns node("relay", nearby = false)
            assertEquals(PhoneReach.RELAY_ONLY, transport.reach())
        }

    @Test
    fun `no phone or a failed lookup is out of reach`() =
        runTest {
            coEvery { phones.select() } returns null
            assertEquals(PhoneReach.UNREACHABLE, transport.reach())

            coEvery { phones.select() } throws IllegalStateException("Play services unavailable")
            assertEquals(PhoneReach.UNREACHABLE, transport.reach())
        }

    @Test
    fun `a request goes to the selected phone and returns its answer`() =
        runTest {
            coEvery { phones.select() } returns node("phone", nearby = true)
            every { messageClient.sendRequest("phone", WearPaths.RECEIPTS, any()) } returns Tasks.forResult("answer".encodeToByteArray())

            assertEquals("answer", transport.request(WearPaths.RECEIPTS, byteArrayOf()).getOrThrow().decodeToString())
        }

    @Test
    fun `a request with no phone in reach fails`() =
        runTest {
            coEvery { phones.select() } returns null

            assertTrue(transport.request(WearPaths.RECEIPTS, byteArrayOf()).isFailure)
        }

    @Test
    fun `every write is urgent`() =
        runTest {
            transport.put("/logdate/v2/w2p/record/note/x", mapOf("k" to "v"))

            coVerify { dataLayer.putDataItem("/logdate/v2/w2p/record/note/x", mapOf("k" to "v"), urgent = true) }
        }

    private fun node(
        nodeId: String,
        nearby: Boolean,
    ): Node =
        mockk {
            every { id } returns nodeId
            every { isNearby } returns nearby
        }
}
