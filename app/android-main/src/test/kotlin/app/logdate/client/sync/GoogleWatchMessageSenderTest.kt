package app.logdate.client.sync

import app.logdate.client.watch.WatchNodeSelector
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [GoogleWatchMessageSender], which messages every reachable watch running LogDate, each one
 * on its own.
 */
class GoogleWatchMessageSenderTest {
    private val capabilityClient = mockk<CapabilityClient>()
    private val messageClient = mockk<MessageClient>()
    private val sender = GoogleWatchMessageSender(WatchNodeSelector(capabilityClient), messageClient)

    @Test
    fun `every reachable watch running LogDate gets the message`() =
        runTest {
            givenWatches(watch("pixel-watch"), watch("galaxy-watch"))
            every { messageClient.sendMessage(any(), PATH, any()) } returns Tasks.forResult(1)

            assertTrue(sender.send(PATH, ByteArray(0)))

            verify(exactly = 1) { messageClient.sendMessage("pixel-watch", PATH, any()) }
            verify(exactly = 1) { messageClient.sendMessage("galaxy-watch", PATH, any()) }
        }

    @Test
    fun `a watch that cannot be reached does not stop the others`() =
        runTest {
            givenWatches(watch("gone-watch"), watch("pixel-watch"))
            every { messageClient.sendMessage("gone-watch", PATH, any()) } returns
                Tasks.forException(IllegalStateException("TARGET_NODE_NOT_CONNECTED"))
            every { messageClient.sendMessage("pixel-watch", PATH, any()) } returns Tasks.forResult(1)

            assertTrue(sender.send(PATH, ByteArray(0)))

            verify(exactly = 1) { messageClient.sendMessage("pixel-watch", PATH, any()) }
        }

    @Test
    fun `nothing is sent when no reachable watch runs LogDate`() =
        runTest {
            givenWatches()

            assertFalse(sender.send(PATH, ByteArray(0)))

            verify(exactly = 0) { messageClient.sendMessage(any(), any(), any()) }
        }

    @Test
    fun `a message no watch accepted is reported as not sent`() =
        runTest {
            givenWatches(watch("pixel-watch"))
            every { messageClient.sendMessage("pixel-watch", PATH, any()) } returns
                Tasks.forException(IllegalStateException("TARGET_NODE_NOT_CONNECTED"))

            assertFalse(sender.send(PATH, ByteArray(0)))
        }

    @Test
    fun `a watch lookup that fails is reported as not sent`() =
        runTest {
            every { capabilityClient.getCapability(any(), any()) } returns Tasks.forException(IllegalStateException("API_UNAVAILABLE"))

            assertFalse(sender.send(PATH, ByteArray(0)))
        }

    @Test
    fun `the payload reaches each watch unchanged`() =
        runTest {
            givenWatches(watch("pixel-watch"))
            val sent = slot<ByteArray>()
            every { messageClient.sendMessage("pixel-watch", PATH, capture(sent)) } returns Tasks.forResult(1)

            sender.send(PATH, PAYLOAD)

            assertContentEquals(PAYLOAD, sent.captured)
        }

    private fun givenWatches(vararg watches: Node) {
        val info = mockk<CapabilityInfo> { every { nodes } returns watches.toSet() }
        every { capabilityClient.getCapability(WatchNodeSelector.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE) } returns
            Tasks.forResult(info)
    }

    private fun watch(id: String): Node =
        mockk {
            every { this@mockk.id } returns id
            every { isNearby } returns true
            every { displayName } returns id
        }

    private companion object {
        const val PATH = "/logdate/notes/550e8400-e29b-41d4-a716-446655440000/ack"
        val PAYLOAD = """{"lastUpdated":1710000000123}""".encodeToByteArray()
    }
}
