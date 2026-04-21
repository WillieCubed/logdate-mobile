package app.logdate.client.sync

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the Google Play Services implementation of the [PhoneWearTransport] interface.
 *
 * These tests ensure that [GooglePhoneWearTransport] correctly interacts with the
 * Android Wearable [DataClient] and [ChannelClient] to push data items and manage
 * byte-stream channels for cross-device communication.
 */
class GooglePhoneWearTransportTest {

    private val dataClient = mockk<DataClient>()
    private val channelClient = mockk<ChannelClient>()

    @Test
    fun `putDataItem returns true when data client succeeds`() = runTest {
        val request = mockk<PutDataRequest>(relaxed = true)
        val transport =
            GooglePhoneWearTransport(
                dataClient = dataClient,
                channelClient = channelClient,
                putDataRequestFactory = PhoneWearPutDataRequestFactory { _, _ -> request },
            )
        every { dataClient.putDataItem(any()) } returns Tasks.forResult(mockk<DataItem>(relaxed = true))

        val success = transport.putDataItem(path = "/logdate/notes/1", data = mapOf("id" to "1"))

        assertTrue(success)
        verify(exactly = 1) { dataClient.putDataItem(request) }
    }

    @Test
    fun `putDataItem returns false when data client fails`() = runTest {
        val request = mockk<PutDataRequest>(relaxed = true)
        val transport =
            GooglePhoneWearTransport(
                dataClient = dataClient,
                channelClient = channelClient,
                putDataRequestFactory = PhoneWearPutDataRequestFactory { _, _ -> request },
            )
        every { dataClient.putDataItem(any()) } returns Tasks.forException(IllegalStateException("boom"))

        val success = transport.putDataItem(path = "/logdate/notes/1", data = mapOf("id" to "1"))

        assertFalse(success)
    }

    @Test
    fun `streamToNode copies bytes into output stream and closes channel`() = runTest {
        val transport = GooglePhoneWearTransport(dataClient, channelClient)
        val payload = "phone-audio".encodeToByteArray()
        val outputStream = ByteArrayOutputStream()
        val channel = mockk<ChannelClient.Channel>()

        every { channelClient.openChannel("watch-node", "/logdate/notes/1/audio") } returns Tasks.forResult(channel)
        every { channelClient.getOutputStream(channel) } returns Tasks.forResult(outputStream)
        every { channelClient.close(channel) } returns Tasks.forResult<Void>(null)

        val success =
            transport.streamToNode(
                nodeId = "watch-node",
                channelPath = "/logdate/notes/1/audio",
                inputStream = ByteArrayInputStream(payload),
            )

        assertTrue(success)
        assertContentEquals(payload, outputStream.toByteArray())
        verify(exactly = 1) { channelClient.close(channel) }
    }

    @Test
    fun `streamToNode returns false when channel open fails`() = runTest {
        val transport = GooglePhoneWearTransport(dataClient, channelClient)
        every { channelClient.openChannel("watch-node", "/logdate/notes/1/audio") } returns
            Tasks.forException(IllegalStateException("boom"))

        val success =
            transport.streamToNode(
                nodeId = "watch-node",
                channelPath = "/logdate/notes/1/audio",
                inputStream = ByteArrayInputStream("payload".encodeToByteArray()),
            )

        assertFalse(success)
    }
}
