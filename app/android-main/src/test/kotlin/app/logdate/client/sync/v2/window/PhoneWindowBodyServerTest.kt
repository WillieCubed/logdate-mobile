package app.logdate.client.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.BodyRef
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WindowBodyRequest
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PhoneWindowBodyServerTest {
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

    @Test fun `current window body is served once at the requested offset with temporary cleanup`() =
        runTest {
            seed()
            val ticket = assertNotNull(server.prepare("watch", request.copy(offset = 7)))
            assertTrue(
                server.serve("watch", ticket.channelPath) { file, offset, length, current ->
                    assertTrue(current())
                    assertEquals(7L, offset)
                    assertEquals(bytes.size - 7L, length)
                    assertEquals(
                        note.content,
                        NoteEnvelopeBody
                            .decode(
                                app.logdate.client.sync.datalayer.v2.WearJson.json
                                    .parseToJsonElement(file.readText()),
                            ).let {
                                (it as JournalNote.Text).content
                            },
                    )
                },
            )
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertFalse(server.serve("watch", ticket.channelPath) { _, _, _, _ -> error("Ticket reused") })
        }

    @Test fun `foreign context unlisted note old revision wrong hash bounds and schema are rejected`() =
        runTest {
            seed()
            for (bad in listOf(
                request.copy(context = peer.copy(phoneNode = "foreign")),
                request.copy(noteId = Uuid.random().toString()),
                request.copy(contentVersion = 3),
                request.copy(revision = 2),
                request.copy(body = request.body.copy(sha256 = "0".repeat(64))),
                request.copy(body = request.body.copy(sizeBytes = Long.MAX_VALUE)),
                request.copy(offset = -1),
                request.copy(schema = 2),
            )) {
                assertNull(server.prepare("watch", bad))
            }
            assertNull(server.prepare("foreign", request))
        }

    @Test fun `same timestamp body replacement and removal invalidate an issued ticket`() =
        runTest {
            seed()
            val ticket = assertNotNull(server.prepare("watch", request))
            stored = note.copy(content = "changed")
            assertFalse(server.serve("watch", ticket.channelPath) { _, _, _, _ -> error("Changed body served") })
            stored = note
            val next = assertNotNull(server.prepare("watch", request))
            manifest.write(peer, PublishedWindow())
            assertFalse(server.serve("watch", next.channelPath) { _, _, _, _ -> error("Removed body served") })
        }

    @Test fun `wrong node cannot consume a ticket and cancellation cleans temporary files`() =
        runTest {
            seed()
            val ticket = assertNotNull(server.prepare("watch", request))
            assertFalse(server.serve("foreign", ticket.channelPath) { _, _, _, _ -> error("Foreign node served") })
            assertFailsWith<CancellationException> {
                server.serve("watch", ticket.channelPath) { _, _, _, _ -> throw CancellationException("Stopped") }
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test fun `revocation during a send invalidates the current guard`() =
        runTest {
            seed()
            val ticket = assertNotNull(server.prepare("watch", request))
            assertFalse(
                server.serve("watch", ticket.channelPath) { _, _, _, current ->
                    active = false
                    assertFalse(current())
                },
            )
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test fun `expired tickets are refused`() =
        runTest {
            seed()
            val ticket = assertNotNull(server.prepare("watch", request))
            now = 60001
            assertFalse(server.serve("watch", ticket.channelPath) { _, _, _, _ -> error("Expired ticket served") })
        }
}
