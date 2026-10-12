package app.logdate.client.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.PhoneReadChannels
import app.logdate.client.sync.datalayer.v2.BodyRef
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WindowBodyPaths
import app.logdate.client.sync.datalayer.v2.WindowBodyRequest
import app.logdate.client.sync.datalayer.v2.WindowBodyTicket
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PhoneWindowBodyEndpointTest {
    private val directory = Files.createTempDirectory("window-bodies").toFile()
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var active = true
    private var now = 0L
    private val note =
        JournalNote.Text(
            uid = Uuid.random(),
            content = "long text".repeat(15000),
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(2),
        )
    private var stored: JournalNote? = note
    private val peers =
        mockk<PhoneDeliveryPeers> {
            coEvery { current(any()) } answers { peer.takeIf { active && firstArg<String>() == peer.watchNode } }
            coEvery { isCurrent(any()) } answers { active && firstArg<PeerDeliveryContext>() == peer }
        }
    private val notes = mockk<JournalNotesRepository> { coEvery { getNoteById(any()) } answers { stored } }
    private val manifest = WindowManifestStore(MemoryKeyValueStorage())
    private val bytes = NoteEnvelopeBody.encode(note).toString().encodeToByteArray()
    private val request =
        WindowBodyRequest(
            context = peer,
            noteId = note.uid.toString(),
            contentVersion = 2,
            revision = 1,
            body = BodyRef(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong()),
        )
    private val server = PhoneWindowBodyServer(peers, notes, manifest, directory) { now }

    private suspend fun seed() {
        manifest.write(peer, PublishedWindow(items = mapOf(note.uid.toString() to PublishedItem(2, emptySet(), 1))))
    }

    @After fun cleanUp() {
        directory.deleteRecursively()
    }

    private val channels =
        mockk<ChannelClient> {
            every { close(any()) } returns Tasks.forResult<Void>(null)
        }
    private val endpoint = PhoneWindowBodyEndpoint(server, channels)

    @Test fun `valid request returns a ticket that serves the current published body`() =
        runTest {
            seed()
            val answer =
                assertNotNull(
                    endpoint.answer(
                        "watch",
                        WindowBodyPaths.RANGE,
                        WearJson.encodeToBytes(WindowBodyRequest.serializer(), request),
                    ),
                )
            val ticket = (WearJson.decode(answer, 1, WindowBodyTicket.serializer()) as Decoded.Supported).value
            assertEquals(request, ticket.request)
            assertTrue(
                server.serve("watch", ticket.channelPath) { file, _, length, current ->
                    assertTrue(current())
                    assertEquals(bytes.size.toLong(), length)
                    assertTrue(file.readBytes().contentEquals(bytes))
                },
            )
        }

    @Test fun `wrong node schema malformed oversized and unrelated RPC requests are refused`() =
        runTest {
            seed()
            val payload = WearJson.encodeToBytes(WindowBodyRequest.serializer(), request)
            assertNull(endpoint.answer("foreign", WindowBodyPaths.RANGE, payload))
            assertNull(endpoint.answer("watch", "/logdate/legacy", payload))
            assertNull(endpoint.answer("watch", WindowBodyPaths.RANGE, ByteArray(8193)))
            assertNull(endpoint.answer("watch", WindowBodyPaths.RANGE, "bad".encodeToByteArray()))
            for (schema in listOf(0, 2)) {
                assertNull(
                    endpoint.answer(
                        "watch",
                        WindowBodyPaths.RANGE,
                        WearJson.encodeToBytes(WindowBodyRequest.serializer(), request.copy(schema = schema)),
                    ),
                )
            }
        }

    @Test fun `an unauthorized channel is closed without sending files`() =
        runTest {
            val channel =
                mockk<ChannelClient.Channel> {
                    every { nodeId } returns "foreign"
                    every { path } returns WindowBodyPaths.CHANNEL_PREFIX + Uuid.random()
                }
            endpoint.serve(channel)
            verify(exactly = 1) { channels.close(channel) }
            verify(exactly = 0) { channels.sendFile(any(), any(), any(), any()) }
        }

    @Test fun `listener routing reaches the body endpoint and recognizes only its channel prefix`() =
        runTest {
            seed()
            val router = PhoneReadChannels({ null }, { endpoint })
            val answer =
                assertNotNull(
                    router.answer(
                        "watch",
                        WindowBodyPaths.RANGE,
                        WearJson.encodeToBytes(WindowBodyRequest.serializer(), request),
                    ),
                )
            val ticket = (WearJson.decode(answer, 1, WindowBodyTicket.serializer()) as Decoded.Supported).value
            assertEquals(request, ticket.request)
            assertTrue(router.recognizes(ticket.channelPath))
            assertFalse(router.recognizes("/logdate/v2/p2w/window-body-foreign/a"))
            assertNull(router.answer("watch", "/unknown", ByteArray(0)))
            val channel =
                mockk<ChannelClient.Channel> {
                    every { nodeId } returns "foreign"
                    every { path } returns ticket.channelPath
                }
            assertTrue(router.serve(channel))
            verify(exactly = 1) { channels.close(channel) }
        }
}
