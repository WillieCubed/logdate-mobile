package app.logdate.client.sync.v2

import app.logdate.client.sync.PhoneWearTransport
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentCodec
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.WearableStatusCodes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [PhonePeerLink], the phone's Play services side of the shared state document.
 */
class PhonePeerLinkTest {
    private val transport = mockk<PhoneWearTransport>()
    private val capabilityClient = mockk<CapabilityClient>()
    private val link =
        PhonePeerLink(
            transport = transport,
            dataClient = mockk<DataClient>(),
            capabilityClient = capabilityClient,
            nodeClient = mockk<NodeClient>(),
        )

    @Test
    fun `the state goes out as one urgent data item`() =
        runTest {
            val document = StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState())
            coEvery { transport.putDataItem(any(), any(), any()) } returns true

            assertTrue(link.publish(document))
            coVerify { transport.putDataItem("/logdate/state", StateDocumentCodec.toDataMap(document), true) }
        }

    @Test
    fun `adding a capability that is already advertised counts as done`() =
        runTest {
            every { capabilityClient.addLocalCapability(PHONE_V2) } returns
                Tasks.forException(ApiException(Status(WearableStatusCodes.DUPLICATE_CAPABILITY)))

            assertTrue(link.add(PHONE_V2))
        }

    @Test
    fun `removing a capability that was never advertised counts as done`() =
        runTest {
            every { capabilityClient.removeLocalCapability(PHONE_V2) } returns
                Tasks.forException(ApiException(Status(WearableStatusCodes.UNKNOWN_CAPABILITY)))

            assertTrue(link.remove(PHONE_V2))
        }

    @Test
    fun `a successful capability change is done`() =
        runTest {
            every { capabilityClient.addLocalCapability(PHONE_V2) } returns Tasks.forResult<Void>(null)

            assertTrue(link.add(PHONE_V2))
        }

    @Test
    fun `any other capability failure is not done`() =
        runTest {
            every { capabilityClient.addLocalCapability(PHONE_V2) } returns Tasks.forException(ApiException(Status(13)))
            every { capabilityClient.removeLocalCapability(PHONE_V2) } returns Tasks.forException(IllegalStateException("API_UNAVAILABLE"))

            assertFalse(link.add(PHONE_V2))
            assertFalse(link.remove(PHONE_V2))
        }

    @Test
    fun `the watch advertising version 2 is reported`() =
        runTest {
            val info = mockk<CapabilityInfo>()
            every { info.nodes } returns setOf(mockk<Node>())
            every { capabilityClient.addListener(any(), WATCH_V2) } returns Tasks.forResult<Void>(null)
            every { capabilityClient.removeListener(any(), WATCH_V2) } returns Tasks.forResult(true)
            every { capabilityClient.getCapability(WATCH_V2, CapabilityClient.FILTER_REACHABLE) } returns Tasks.forResult(info)

            assertTrue(link.observe(WATCH_V2).first())
        }

    private companion object {
        const val PHONE_V2 = "logdate_phone_sync_v2"
        const val WATCH_V2 = "logdate_watch_sync_v2"
    }
}
