package app.logdate.wear.sync.v2.media

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PhoneMediaDescriptor
import app.logdate.client.sync.datalayer.v2.PhoneMediaRangeRequest
import app.logdate.client.sync.datalayer.v2.PhoneMediaRangeTicket
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearMediaPaths
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.sync.v2.delivery.DataLayerTransportV2
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class GoogleMediaRangeSourceTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val identity = WatchMediaIdentity(peer, Uuid.random(), 2, 11, "a".repeat(64))
    private val descriptor =
        PhoneMediaDescriptor(
            context = peer,
            noteId = identity.noteId.toString(),
            contentVersion = 2,
            sizeBytes = 11,
            sha256 = identity.sha256,
        )
    private val path = WearMediaPaths.CHANNEL_PREFIX + Uuid.random()
    private var response = PhoneMediaRangeTicket(media = descriptor, offset = 5, channelPath = path)
    private var active = true
    private val peers =
        mockk<WatchDeliveryPeer> {
            coEvery { isCurrent(any()) } answers
                { active && firstArg<PeerDeliveryContext>() == peer }
        }
    private val transport =
        mockk<DataLayerTransportV2> {
            coEvery { request(WearMediaPaths.RANGE, any()) } coAnswers {
                val decoded = WearJson.json.decodeFromString(PhoneMediaRangeRequest.serializer(), secondArg<ByteArray>().decodeToString())
                assertEquals(5L, decoded.offset)
                assertEquals(descriptor, decoded.media)
                Result.success(WearJson.encodeToBytes(PhoneMediaRangeTicket.serializer(), response))
            }
        }
    private val channel =
        mockk<ChannelClient.Channel> {
            every { nodeId } returns "phone"
            every { path } returns this@GoogleMediaRangeSourceTest.path
        }
    private val input = ByteArrayInputStream(" audio".toByteArray())
    private val channels =
        mockk<ChannelClient> {
            every { openChannel("phone", this@GoogleMediaRangeSourceTest.path) } returns Tasks.forResult(channel)
            every { getInputStream(channel) } returns Tasks.forResult(input)
            every { close(channel) } returns Tasks.forResult<Void>(null)
        }
    private val source = GoogleMediaRangeSource(peers, transport, channels)

    @Test fun `opens only the pinned phone and ticket and reads its range`() =
        runTest {
            val connection = source.open(MediaRangeRequest(identity, 5))
            try {
                val buffer = ByteArray(20)
                assertEquals(6, connection.read(buffer))
                assertEquals(" audio", buffer.decodeToString(0, 6))
                assertEquals(identity, connection.identity)
                assertEquals(5L, connection.offset)
                assertEquals("phone", connection.sourceNodeId)
            } finally {
                connection.close()
            }
            verify { channels.close(channel) }
        }

    @Test fun `mismatched ticket context content offset and path never open channels`() =
        runTest {
            for (bad in listOf(
                response.copy(media = descriptor.copy(context = peer.copy(phoneNode = "other"))),
                response.copy(media = descriptor.copy(contentVersion = 3)),
                response.copy(offset = 6),
                response.copy(schema = 2),
                response.copy(channelPath = "/logdate/notes/legacy"),
            )) {
                response = bad
                assertFailsWith<IOException> { source.open(MediaRangeRequest(identity, 5)) }
            }
            verify(exactly = 0) { channels.openChannel(any(), any()) }
        }

    @Test fun `foreign channel source closes without exposing its input`() =
        runTest {
            every { channel.nodeId } returns "other"
            assertFailsWith<IOException> { source.open(MediaRangeRequest(identity, 5)) }
            verify { channels.close(channel) }
            verify(exactly = 0) { channels.getInputStream(any()) }
        }

    @Test fun `revoked exchange cannot open a range`() =
        runTest {
            active = false
            assertFailsWith<IOException> { source.open(MediaRangeRequest(identity, 5)) }
            verify(exactly = 0) { channels.openChannel(any(), any()) }
        }

    @Test fun `a channel that opens after cancellation is closed`() =
        runTest {
            val opening = TaskCompletionSource<ChannelClient.Channel>()
            every { channels.openChannel("phone", path) } returns opening.task
            val pending = launch(start = CoroutineStart.UNDISPATCHED) { source.open(MediaRangeRequest(identity, 5)) }
            pending.cancelAndJoin()
            opening.setResult(channel)
            verify { channels.close(channel) }
            verify(exactly = 0) { channels.getInputStream(any()) }
        }

    @Test fun `late input cleanup failures do not escape the cancelled request`() =
        runTest {
            val opening = TaskCompletionSource<java.io.InputStream>()
            every { channels.getInputStream(channel) } returns opening.task
            val pending = launch(start = CoroutineStart.UNDISPATCHED) { source.open(MediaRangeRequest(identity, 5)) }
            pending.cancelAndJoin()
            var closed = false
            val lateInput =
                object : java.io.InputStream() {
                    override fun read(): Int = -1

                    override fun close() {
                        closed = true
                        throw IOException("Already disconnected")
                    }
                }
            kotlin.test.assertTrue(runCatching { opening.setResult(lateInput) }.isSuccess)
            kotlin.test.assertTrue(closed)
            verify { channels.close(channel) }
        }
}
