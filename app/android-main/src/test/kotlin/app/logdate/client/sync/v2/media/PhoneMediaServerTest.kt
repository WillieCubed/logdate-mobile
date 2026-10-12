package app.logdate.client.sync.v2.media

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PhoneMediaInfoRequest
import app.logdate.client.sync.datalayer.v2.PhoneMediaRangeRequest
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PhoneMediaServerTest {
    private val directory = Files.createTempDirectory("phone-media").toFile()
    private val file = java.io.File(directory, "recording.m4a").apply { writeText("phone audio") }
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var active = true
    private var now = 0L
    private var note: JournalNote? =
        JournalNote.Audio(
            uid = Uuid.random(),
            mediaRef = "logdate-media://recordings/recording.m4a",
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(2),
            durationMs = 500,
        )
    private val notes =
        mockk<JournalNotesRepository> {
            coEvery { getNoteById(any()) } answers { note?.takeIf { it.uid == firstArg<Uuid>() } }
        }
    private val peers =
        mockk<PhoneDeliveryPeers> {
            coEvery { current(any()) } answers { peer.takeIf { active && firstArg<String>() == peer.watchNode } }
            coEvery { isCurrent(any()) } answers { active && firstArg<PeerDeliveryContext>() == peer }
        }
    private val server = PhoneMediaServer(peers, notes, { file.absolutePath }, { now })
    private val info get() = PhoneMediaInfoRequest(context = peer, noteId = note!!.uid.toString(), contentVersion = 2)

    @After fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test fun `describes the current owned recording and serves a one use byte range`() =
        runTest {
            val media = assertNotNull(server.describe("watch", info))
            assertEquals(11L, media.sizeBytes)
            val ticket = assertNotNull(server.prepare("watch", PhoneMediaRangeRequest(media = media, offset = 5)))
            var sent = ""
            assertTrue(
                server.serve("watch", ticket.channelPath) { source, offset, length, current ->
                    assertTrue(current())
                    assertEquals(5L, offset)
                    assertEquals(6L, length)
                    sent = source.readText().substring(offset.toInt())
                },
            )
            assertEquals(" audio", sent)
            assertFalse(server.serve("watch", ticket.channelPath) { _, _, _, _ -> error("Replayed ticket") })
        }

    @Test fun `foreign node schema context and note version cannot obtain descriptors`() =
        runTest {
            assertNull(server.describe("other", info))
            assertNull(server.describe("watch", info.copy(schema = 2)))
            assertNull(server.describe("watch", info.copy(contentVersion = 3)))
            assertNull(server.describe("watch", info.copy(context = peer.copy(phoneNode = "other"))))
            assertNull(server.describe("watch", info.copy(noteId = "../recording.m4a")))
        }

    @Test fun `range cannot substitute size digest or offset`() =
        runTest {
            val media = assertNotNull(server.describe("watch", info))
            for (request in listOf(
                PhoneMediaRangeRequest(media = media.copy(sha256 = "0".repeat(64)), offset = 0),
                PhoneMediaRangeRequest(media = media.copy(sizeBytes = 12), offset = 0),
                PhoneMediaRangeRequest(media = media, offset = -1),
                PhoneMediaRangeRequest(media = media, offset = 12),
                PhoneMediaRangeRequest(schema = 0, media = media, offset = 0),
            )) {
                assertNull(server.prepare("watch", request))
            }
        }

    @Test fun `revocation expiry and changed note invalidate prepared channels`() =
        runTest {
            val media = assertNotNull(server.describe("watch", info))
            val revoked = assertNotNull(server.prepare("watch", PhoneMediaRangeRequest(media = media, offset = 0)))
            active = false
            assertFalse(server.serve("watch", revoked.channelPath) { _, _, _, _ -> error("Revoked") })
            active = true
            val expired = assertNotNull(server.prepare("watch", PhoneMediaRangeRequest(media = media, offset = 0)))
            now = 60_001
            assertFalse(server.serve("watch", expired.channelPath) { _, _, _, _ -> error("Expired") })
            val changed = assertNotNull(server.prepare("watch", PhoneMediaRangeRequest(media = media, offset = 0)))
            note = (note as JournalNote.Audio).copy(mediaRef = "changed")
            assertFalse(server.serve("watch", changed.channelPath) { _, _, _, _ -> error("Changed") })
        }

    @Test fun `changing file bytes after negotiation cannot serve a prepared range`() =
        runTest {
            val media = assertNotNull(server.describe("watch", info))
            val ticket = assertNotNull(server.prepare("watch", PhoneMediaRangeRequest(media = media, offset = 0)))
            file.writeText("wrong audio")
            assertFalse(server.serve("watch", ticket.channelPath) { _, _, _, _ -> error("Changed bytes") })
        }

    @Test fun `transfer guard notices a revoked exchange during sending`() =
        runTest {
            val media = assertNotNull(server.describe("watch", info))
            val ticket = assertNotNull(server.prepare("watch", PhoneMediaRangeRequest(media = media, offset = 0)))
            assertFalse(
                server.serve("watch", ticket.channelPath) { _, _, _, current ->
                    assertTrue(current())
                    active = false
                    assertFalse(current())
                },
            )
        }
}
