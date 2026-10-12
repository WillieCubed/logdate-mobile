package app.logdate.wear.sync.v2.media

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PhoneMediaDescriptor
import app.logdate.client.sync.datalayer.v2.PhoneMediaInfoRequest
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearMediaPaths
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.playback.SyncedAudioWatchMediaCache
import app.logdate.wear.sync.SyncedAudioCache
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WatchPhoneMediaTest {
    private val directory = Files.createTempDirectory("phone-media-watch").toFile()
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var active = true
    private var supported = true
    private var reachable = true
    private var requested = 0
    private val bytes = "phone audio".toByteArray()
    private val note =
        JournalNote.Audio(
            uid = Uuid.random(),
            mediaRef = "logdate-media://recordings/phone.m4a",
            durationMs = 1000,
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(2),
        )
    private var stored: JournalNote? = note
    private val peers =
        mockk<WatchDeliveryPeer> {
            coEvery { current() } answers { peer.takeIf { active } }
            coEvery { isCurrent(any()) } answers { active && firstArg<PeerDeliveryContext>() == peer }
        }
    private val notes = mockk<JournalNotesRepository> { coEvery { getNoteById(note.uid) } answers { stored } }
    private val cache = SyncedAudioWatchMediaCache(SyncedAudioCache(directory), { Long.MAX_VALUE })
    private val storage = MemoryKeyValueStorage()
    private var descriptor =
        PhoneMediaDescriptor(
            context = peer,
            noteId = note.uid.toString(),
            contentVersion = 2,
            sizeBytes = bytes.size.toLong(),
            sha256 =
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                },
            mediaRef = note.mediaRef,
        )
    private var duringRead: () -> Unit = {}
    private val rangeSource =
        MediaRangeSource { request ->
            if (!reachable) throw IOException("Phone away")
            object : MediaRangeConnection {
                override val identity = request.identity
                override val sourceNodeId = peer.phoneNode
                override val offset = request.offset
                private var read = false

                override suspend fun read(buffer: ByteArray): Int {
                    if (read) return -1
                    read = true
                    bytes.copyInto(buffer)
                    duringRead()
                    return bytes.size
                }

                override suspend fun close() = Unit
            }
        }

    private fun repository() =
        WatchPhoneMedia(peers, notes, cache, storage, { captured, path, payload ->
            requested++
            assertEquals(peer, captured)
            assertEquals(WearMediaPaths.INFO, path)
            val info = WearJson.json.decodeFromString(PhoneMediaInfoRequest.serializer(), payload.decodeToString())
            assertEquals(note.uid.toString(), info.noteId)
            if (!reachable) {
                Result.failure(IOException("Phone away"))
            } else {
                Result.success(WearJson.encodeToBytes(PhoneMediaDescriptor.serializer(), descriptor))
            }
        }, { rangeSource }, { supported })

    @After fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test fun `uncached playback prepares a pinned stream without changing or downloading the note`() =
        runTest {
            val repo = repository()
            val uri = repo.prepare(note)
            assertTrue(uri.startsWith(WatchPhoneMedia.SCHEME))
            assertEquals(note.mediaRef, repo.identity(uri).mediaRef)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertEquals(note, stored)
            coVerify(exactly = 0) { notes.create(any()) }
        }

    @Test fun `a verified download plays offline after recreating the repository`() =
        runTest {
            val file = repository().download(note)
            assertEquals("phone audio", file.readText())
            reachable = false
            val before = requested
            val offline = repository()
            val uri = offline.prepare(note)
            assertTrue(uri.startsWith(WatchPhoneMedia.SCHEME))
            val identity = offline.identity(uri)
            val range = offline.source(identity).open(MediaRangeRequest(identity, 0))
            try {
                val output = ByteArray(bytes.size)
                assertEquals(bytes.size, range.read(output))
                assertEquals("phone audio", output.decodeToString())
            } finally {
                range.close()
            }
            assertEquals(before, requested)
            assertEquals(note.mediaRef, (stored as JournalNote.Audio).mediaRef)
        }

    @Test fun `an unproven cache file never authorizes offline playback`() =
        runTest {
            File(directory, "${note.uid}.m4a").writeText("old account audio")
            reachable = false
            assertFailsWith<IOException> { repository().prepare(note) }
        }

    @Test fun `same timestamp different reference and foreign descriptors cannot authorize playback`() =
        runTest {
            for (bad in listOf(
                descriptor.copy(mediaRef = "other"),
                descriptor.copy(context = peer.copy(phoneNode = "other")),
                descriptor.copy(noteId = Uuid.random().toString()),
                descriptor.copy(contentVersion = 3),
                descriptor.copy(schema = 2),
            )) {
                descriptor = bad
                assertFailsWith<IOException> { repository().prepare(note) }
            }
        }

    @Test fun `removed notes and revoked exchanges invalidate stream permits`() =
        runTest {
            val repo = repository()
            val uri = repo.prepare(note)
            stored = null
            assertFailsWith<IOException> { repo.identity(uri) }
            stored = note
            active = false
            assertFailsWith<IOException> { repo.identity(uri) }
        }

    @Test fun `edits or revocation during a download cannot publish cache audio`() =
        runTest {
            duringRead = { stored = note.copy(mediaRef = "changed") }
            assertFailsWith<IOException> { repository().download(note) }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test fun `corrupt cache bytes cannot play as a verified complete file`() =
        runTest {
            val repo = repository()
            val file = repo.download(note)
            file.writeText("wrong audio")
            reachable = false
            assertFailsWith<IOException> { repo.prepare(note) }
        }

    @Test fun `unsupported phones never receive media requests`() =
        runTest {
            supported = false
            assertFailsWith<IOException> { repository().prepare(note) }
            assertEquals(0, requested)
        }
}
