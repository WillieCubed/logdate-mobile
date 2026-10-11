package app.logdate.wear.sync.v2.delivery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LinkedDataLayerTransportV2Test {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val peers = mockk<WatchDeliveryPeer>()
    private var current = true
    private val dataLayer = mockk<WearDataLayerClient>(relaxed = true)
    private val capabilities = mockk<CapabilityClient>()
    private val channels = mockk<ChannelClient>()
    private val messages = mockk<MessageClient>()
    private val channel = mockk<ChannelClient.Channel>()
    private val output = ByteArrayOutputStream()
    private val transport = LinkedDataLayerTransportV2(peer, peers, dataLayer, capabilities, channels, messages)
    private val note =
        JournalNote.Text(
            content = "watch",
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
        )
    private val path = WearPaths.watchRecord(RecordKind.NOTE, note.uid)
    private val mediaPath = WearPaths.recordMedia(RecordKind.NOTE, note.uid, peer)
    private val file = File.createTempFile("watch-capture-", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    private var reachable = setOf(node("another-phone", true), node("phone", false))

    init {
        coEvery { peers.isCurrent(any()) } answers { current && firstArg<PeerDeliveryContext>() == peer }
        every { capabilities.getCapability(WearProtocol.PHONE_CAPABILITY_V2, CapabilityClient.FILTER_REACHABLE) } answers {
            Tasks.forResult(mockk<CapabilityInfo> { every { nodes } answers { reachable } })
        }
        every { messages.sendRequest("phone", WearPaths.RECEIPTS, any()) } returns Tasks.forResult(byteArrayOf(9))
        every { channels.openChannel("phone", mediaPath) } returns Tasks.forResult(channel)
        every { channels.getOutputStream(channel) } returns Tasks.forResult(output)
        every { channels.close(channel) } returns Tasks.forResult(null)
        coEvery { dataLayer.putDataItem(any(), any(), true) } returns true
        coEvery { dataLayer.deleteOwnDataItem(any()) } returns true
    }

    @After
    fun clean() {
        file.delete()
    }

    @Test
    fun `reach describes the linked phone instead of preferring another nearby node`() =
        runTest {
            assertEquals(PhoneReach.RELAY_ONLY, transport.reach())
            reachable = setOf(node("phone", true))
            assertEquals(PhoneReach.NEARBY, transport.reach())
        }

    @Test
    fun `requests target the exact linked phone`() =
        runTest {
            assertContentEquals(byteArrayOf(9), transport.request(WearPaths.RECEIPTS, byteArrayOf(4)).getOrThrow())
            verify(exactly = 1) { messages.sendRequest("phone", WearPaths.RECEIPTS, byteArrayOf(4)) }
        }

    @Test
    fun `another reachable phone is never a fallback`() =
        runTest {
            reachable = setOf(node("another-phone", true))
            assertEquals(PhoneReach.UNREACHABLE, transport.reach())
            assertTrue(transport.request(WearPaths.RECEIPTS, byteArrayOf()).isFailure)
            assertFalse(transport.sendFile(mediaPath, file.path))
            verify(exactly = 0) { messages.sendRequest(any(), any(), any()) }
            verify(exactly = 0) { channels.openChannel(any(), any()) }
        }

    @Test
    fun `a response received after relinking is discarded`() =
        runTest {
            every { messages.sendRequest("phone", WearPaths.RECEIPTS, any()) } answers {
                current = false
                Tasks.forResult(byteArrayOf(9))
            }
            assertTrue(transport.request(WearPaths.RECEIPTS, byteArrayOf()).isFailure)
        }

    @Test
    fun `only metadata bound to this exchange is written urgently`() =
        runTest {
            assertFalse(transport.put(path, data(peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 })))))
            assertTrue(transport.put(path, data(peer)))
            coVerify(exactly = 1) { dataLayer.putDataItem(path, data(peer), true) }
        }

    @Test
    fun `a stale link cannot write or delete its old data items`() =
        runTest {
            current = false
            assertFalse(transport.put(path, data(peer)))
            assertFalse(transport.deleteOwn(path))
            coVerify(exactly = 0) { dataLayer.putDataItem(any(), any(), any()) }
            coVerify(exactly = 0) { dataLayer.deleteOwnDataItem(any()) }
        }

    @Test
    fun `audio targets the linked phone and transfers its complete bytes`() =
        runTest {
            assertTrue(transport.sendFile(mediaPath, file.path))
            assertContentEquals(byteArrayOf(1, 2, 3), output.toByteArray())
            verify(exactly = 1) { channels.openChannel("phone", mediaPath) }
            verify(exactly = 1) { channels.close(channel) }
        }

    @Test
    fun `audio without the matching link path cannot open a channel`() =
        runTest {
            assertFalse(transport.sendFile(WearPaths.recordMedia(RecordKind.NOTE, note.uid), file.path))
            verify(exactly = 0) { channels.openChannel(any(), any()) }
        }

    @Test
    fun `relinking while opening audio closes the channel before any byte is sent`() =
        runTest {
            every { channels.openChannel("phone", mediaPath) } answers {
                current = false
                Tasks.forResult(channel)
            }
            assertFalse(transport.sendFile(mediaPath, file.path))
            assertTrue(output.toByteArray().isEmpty())
            verify(exactly = 1) { channels.close(channel) }
        }

    @Test
    fun `cancellation while looking for the linked phone is propagated`() =
        runTest {
            coEvery { peers.isCurrent(any()) } throws CancellationException("stopped")
            assertFailsWith<CancellationException> { transport.request(WearPaths.RECEIPTS, byteArrayOf()) }
        }

    @Test
    fun `relinking during capability lookup sends no request`() =
        runTest {
            every { capabilities.getCapability(any(), any()) } answers {
                current = false
                Tasks.forResult(mockk<CapabilityInfo> { every { nodes } answers { reachable } })
            }
            assertTrue(transport.request(WearPaths.RECEIPTS, byteArrayOf()).isFailure)
            verify(exactly = 0) { messages.sendRequest(any(), any(), any()) }
        }

    @Test
    fun `a silent phone times out without switching recipients`() =
        runTest {
            every { messages.sendRequest("phone", WearPaths.RECEIPTS, any()) } returns TaskCompletionSource<ByteArray>().task
            assertTrue(transport.request(WearPaths.RECEIPTS, byteArrayOf()).isFailure)
            verify(exactly = 1) { messages.sendRequest("phone", WearPaths.RECEIPTS, any()) }
        }

    @Test
    fun `relinking during audio stops before sending another chunk`() =
        runTest {
            file.writeBytes(ByteArray(20_000) { 3 })
            val changing =
                object : ByteArrayOutputStream() {
                    override fun write(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ) {
                        super.write(bytes, offset, length)
                        current = false
                    }
                }
            every { channels.getOutputStream(channel) } returns Tasks.forResult(changing)
            assertFalse(transport.sendFile(mediaPath, file.path))
            assertEquals(8192, changing.size())
            verify(exactly = 1) { channels.close(channel) }
        }

    @Test
    fun `cancellation after opening audio closes its channel and propagates`() =
        runTest {
            every { channels.getOutputStream(channel) } throws CancellationException("stopped")
            assertFailsWith<CancellationException> { transport.sendFile(mediaPath, file.path) }
            verify(exactly = 1) { channels.close(channel) }
        }

    @Test
    fun `a missing local recording closes its opened channel and remains unsent`() =
        runTest {
            assertFalse(transport.sendFile(mediaPath, file.path + ".missing"))
            verify(exactly = 1) { channels.close(channel) }
        }

    @Test
    fun `a current link can remove its delivered data item`() =
        runTest {
            assertTrue(transport.deleteOwn(path))
            coVerify(exactly = 1) { dataLayer.deleteOwnDataItem(path) }
        }

    private fun data(context: PeerDeliveryContext) =
        EnvelopeDataMap.toDataMap(
            NoteEnvelopeBody.envelope(note, RecordOrigin.WATCH, DeliveryPriority.URGENT).copy(context = context),
            sentAt = 1,
        )

    private fun node(
        value: String,
        nearby: Boolean,
    ): Node =
        mockk {
            every { id } returns value
            every { isNearby } returns nearby
        }
}
