package app.logdate.client.sync.v2.media

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PhoneMediaDescriptor
import app.logdate.client.sync.datalayer.v2.PhoneMediaInfoRequest
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearMediaPaths
import app.logdate.client.sync.peer.link.LinkWire
import com.google.android.gms.wearable.ChannelClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class PhoneMediaEndpointTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val info = PhoneMediaInfoRequest(context = peer, noteId = Uuid.random().toString(), contentVersion = 2)
    private val descriptor =
        PhoneMediaDescriptor(context = peer, noteId = info.noteId, contentVersion = 2, sizeBytes = 11, sha256 = "a".repeat(64))
    private val server =
        mockk<PhoneMediaServer> {
            coEvery { describe("watch", info) } returns descriptor
        }
    private val endpoint = PhoneMediaEndpoint(server, mockk<ChannelClient>())

    @Test fun `the info RPC returns the exact verified descriptor`() =
        runTest {
            val response =
                assertNotNull(
                    endpoint.answer("watch", WearMediaPaths.INFO, WearJson.encodeToBytes(PhoneMediaInfoRequest.serializer(), info)),
                )
            assertEquals(descriptor, WearJson.json.decodeFromString(PhoneMediaDescriptor.serializer(), response.decodeToString()))
        }

    @Test fun `unknown oversized malformed and unsupported RPCs do not reach the server`() =
        runTest {
            assertNull(endpoint.answer("watch", "/unknown", ByteArray(0)))
            assertNull(endpoint.answer("watch", WearMediaPaths.INFO, ByteArray(8193)))
            assertNull(endpoint.answer("watch", WearMediaPaths.INFO, "bad".toByteArray()))
            for (schema in listOf(0, 2)) {
                assertNull(
                    endpoint.answer(
                        "watch",
                        WearMediaPaths.INFO,
                        WearJson.encodeToBytes(PhoneMediaInfoRequest.serializer(), info.copy(schema = schema)),
                    ),
                )
            }
            coVerify(exactly = 0) { server.describe(any(), any()) }
        }
}
