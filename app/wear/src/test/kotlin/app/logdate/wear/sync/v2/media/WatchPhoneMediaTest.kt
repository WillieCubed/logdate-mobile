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
import kotlinx.coroutines.async
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
    private var currentPeer = peer
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
            coEvery { current() } answers { currentPeer.takeIf { active } }
            coEvery { isCurrent(any()) } answers { active && firstArg<PeerDeliveryContext>() == currentPeer }
        }
    private val notes = mockk<JournalNotesRepository> { coEvery { getNoteById(note.uid) } answers { stored } }
    private var available = Long.MAX_VALUE
    private val cache = SyncedAudioWatchMediaCache(SyncedAudioCache(directory), { available })
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
    private var duringRead: suspend () -> Unit = {}
    private var duringEnd: suspend () -> Unit = {}
    private val rangeSource =
        MediaRangeSource { request ->
            if (!reachable) throw IOException("Phone away")
            object : MediaRangeConnection {
                override val identity = request.identity
                override val sourceNodeId = peer.phoneNode
                override val offset = request.offset
                private var read = false

                override suspend fun read(buffer: ByteArray): Int {
                    if (read) {
                        duringEnd()
                        return -1
                    }
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

    @Test fun `prefetch cannot download for a stale exchange or an ineligible note`() =
        runTest {
            val repo = repository()
            assertFailsWith<IOException> { repo.download(note, peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))) }
            assertFailsWith<IOException> { repo.download(note, peer) { false } }
            assertEquals(0, requested)
        }

    @Test fun `prefetch eligibility loss during transfer cannot publish bytes`() =
        runTest {
            var eligible = true
            duringRead = { eligible = false }
            assertFailsWith<IOException> { repository().download(note, peer) { eligible } }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test fun `queued downloads cannot switch to a replacement phone exchange`() =
        runTest {
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val proceed = kotlinx.coroutines.CompletableDeferred<Unit>()
            duringRead = {
                started.complete(Unit)
                proceed.await()
            }
            val repo = repository()
            val first = async { runCatching { repo.download(note) } }
            started.await()
            val queued =
                async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    runCatching { repo.download(note) }
                }
            currentPeer = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            proceed.complete(Unit)
            assertTrue(first.await().isFailure)
            assertTrue(queued.await().isFailure)
            assertEquals(1, requested)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    private suspend fun prefetcher(): WatchMediaPrefetcher {
        val members =
            app.logdate.wear.sync.v2.window
                .WindowMembershipStore(storage)
        val id = note.uid.toString()
        val hash =
            app.logdate.wear.sync.v2.window
                .windowNoteFingerprint(note)
        members.write(
            peer,
            app.logdate.wear.sync.v2.window.WindowMembership(
                entries =
                    mapOf(
                        id to
                            app.logdate.wear.sync.v2.window
                                .WindowProjection(1, 2, setOf(hash), hash),
                    ),
                snapshotRevision = 1,
                expected =
                    mapOf(
                        id to
                            app.logdate.client.sync.datalayer.v2
                                .WindowMemberRef(app.logdate.client.sync.datalayer.v2.RecordKind.NOTE, id, 2, 1),
                    ),
            ),
        )
        val outbox = mockk<app.logdate.client.sync.metadata.SyncMetadataService> { coEvery { hasPending(any(), any()) } returns false }
        val repo = repository()
        return WatchMediaPrefetcher(peers, members, notes, outbox, { active }, { supported }, { audio, captured, allowed ->
            repo.download(audio, captured, allowed)
        })
    }

    @Test fun `prefetch fills the shared verified cache and preserves the logical note`() =
        runTest {
            assertEquals(1, prefetcher().run())
            reachable = false
            val repo = repository()
            val identity = repo.identity(repo.prepare(note))
            val range = repo.source(identity).open(MediaRangeRequest(identity, 0))
            try {
                val output = ByteArray(bytes.size)
                assertEquals(bytes.size, range.read(output))
                assertEquals("phone audio", output.decodeToString())
            } finally {
                range.close()
            }
            assertEquals(note, stored)
            assertEquals(1, requested)
        }

    @Test fun `prefetch preserves the recording reserve and removes incomplete cache files`() =
        runTest {
            available = app.logdate.wear.playback.SyncedAudioWatchMediaCache.DEFAULT_RESERVE_BYTES
            assertFailsWith<app.logdate.wear.playback.NoRoomForAudioException> { prefetcher().run() }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertEquals(note, stored)
        }

    @Test fun `eligibility loss at stream completion cannot publish an unowned cache file`() =
        runTest {
            var eligible = true
            duringEnd = { eligible = false }
            assertFailsWith<IOException> { repository().download(note, peer) { eligible } }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

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
