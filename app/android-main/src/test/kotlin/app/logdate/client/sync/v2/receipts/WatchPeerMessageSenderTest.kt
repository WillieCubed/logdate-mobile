package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchPeerMessageSenderTest {
    private val peers = mockk<PhoneDeliveryPeers>()
    private val messages = mockk<MessageClient>()
    private val sender = GoogleWatchPeerMessageSender(messages, peers)
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32)), "phone", "watch", LinkMode.PAIR)
    private val data = byteArrayOf(1, 2)

    @Test
    fun `the current link targets its exact node with an unchanged payload`() =
        runTest {
            coEvery { peers.isCurrent(context) } returns true
            every { messages.sendMessage("watch", "/receipts", data) } returns Tasks.forResult(1)
            assertTrue(sender.send(context, "/receipts", data))
            verify(exactly = 1) { messages.sendMessage("watch", "/receipts", data) }
            verify(exactly = 0) { messages.sendMessage(neq("watch"), any(), any()) }
        }

    @Test
    fun `an expired audience does not send to any node`() =
        runTest {
            coEvery { peers.isCurrent(context) } returns false
            assertFalse(sender.send(context, "/receipts", data))
            verify(exactly = 0) { messages.sendMessage(any(), any(), any()) }
        }

    @Test
    fun `a failed send is reported without choosing another watch`() =
        runTest {
            coEvery { peers.isCurrent(context) } returns true
            every { messages.sendMessage("watch", "/receipts", data) } returns Tasks.forException(IllegalStateException("Disconnected"))
            assertFalse(sender.send(context, "/receipts", data))
            verify(exactly = 0) { messages.sendMessage(neq("watch"), any(), any()) }
        }

    @Test
    fun `a cancelled send propagates cancellation`() =
        runTest {
            coEvery { peers.isCurrent(context) } returns true
            every { messages.sendMessage("watch", "/receipts", data) } returns Tasks.forCanceled()
            assertFailsWith<CancellationException> { sender.send(context, "/receipts", data) }
        }
}
