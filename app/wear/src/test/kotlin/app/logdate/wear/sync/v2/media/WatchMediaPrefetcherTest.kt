package app.logdate.wear.sync.v2.media

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.window.WindowMembership
import app.logdate.wear.sync.v2.window.WindowMembershipStore
import app.logdate.wear.sync.v2.window.WindowProjection
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WatchMediaPrefetcherTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var current: PeerDeliveryContext? = peer
    private var enabled = true
    private var supported = true
    private val peers =
        mockk<WatchDeliveryPeer> {
            coEvery { current() } answers { current }
            coEvery { isCurrent(any()) } answers { current == firstArg<PeerDeliveryContext>() }
        }
    private val members = WindowMembershipStore(MemoryKeyValueStorage())
    private val stored = mutableMapOf<Uuid, JournalNote>()
    private val notes = mockk<JournalNotesRepository> { coEvery { getNoteById(any()) } answers { stored[firstArg()] } }
    private val pending = mutableSetOf<String>()
    private val outbox = mockk<SyncMetadataService> { coEvery { hasPending(any(), any()) } answers { secondArg<String>() in pending } }
    private val downloaded = mutableListOf<Uuid>()
    private var during: suspend (suspend () -> Boolean) -> Unit = { assertTrue(it()) }
    private val prefetcher =
        WatchMediaPrefetcher(peers, members, notes, outbox, { enabled }, { supported }, { note, captured, allowed ->
            assertEquals(peer, captured)
            during(allowed)
            downloaded += note.uid
        })

    private fun audio(version: Long = 1) =
        JournalNote.Audio(
            uid = Uuid.random(),
            mediaRef = "phone.m4a",
            durationMs = 100,
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(version),
        )

    private suspend fun seed(vararg audio: JournalNote.Audio) {
        stored.putAll(audio.associateBy { it.uid })
        members.write(
            peer,
            WindowMembership(
                entries =
                    audio.associate {
                        it.uid.toString() to
                            WindowProjection(
                                1,
                                it.lastUpdated.toEpochMilliseconds(),
                                setOf(
                                    app.logdate.wear.sync.v2.window
                                        .windowNoteFingerprint(it),
                                ),
                                app.logdate.wear.sync.v2.window
                                    .windowNoteFingerprint(it),
                            )
                    },
                snapshotRevision = 1,
                expected =
                    audio.associate {
                        it.uid.toString() to
                            WindowMemberRef(RecordKind.NOTE, it.uid.toString(), it.lastUpdated.toEpochMilliseconds(), 1)
                    },
            ),
        )
    }

    @Test fun `only managed current audio is downloaded newest first`() =
        runTest {
            val older = audio(1)
            val newer = audio(2)
            val local = audio(3)
            seed(older, newer)
            stored[local.uid] = local
            assertEquals(2, prefetcher.run())
            assertEquals(listOf(newer.uid, older.uid), downloaded)
        }

    @Test fun `flag off unsupported phone and missing link do no work`() =
        runTest {
            seed(audio())
            enabled = false
            assertEquals(0, prefetcher.run())
            enabled = true
            supported = false
            assertEquals(0, prefetcher.run())
            supported = true
            current = null
            assertEquals(0, prefetcher.run())
            assertTrue(downloaded.isEmpty())
        }

    @Test fun `pending edits changed versions missing records and text are skipped`() =
        runTest {
            val pendingNote = audio()
            val changed = audio()
            val missing = audio()
            val text = audio()
            seed(pendingNote, changed, missing, text)
            pending += pendingNote.uid.toString()
            stored[changed.uid] = changed.copy(lastUpdated = Instant.fromEpochMilliseconds(3))
            stored.remove(missing.uid)
            stored[text.uid] =
                JournalNote.Text(
                    uid = text.uid,
                    content = "local",
                    creationTimestamp = text.creationTimestamp,
                    lastUpdated = text.lastUpdated,
                )
            assertEquals(0, prefetcher.run())
            assertTrue(downloaded.isEmpty())
        }

    @Test fun `link replacement stops the run and invalidates its active download guard`() =
        runTest {
            seed(audio(1), audio(2))
            during = { guard ->
                current = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
                assertFalse(guard())
            }
            prefetcher.run()
            assertEquals(1, downloaded.size)
        }

    @Test fun `retired projections and incomplete snapshots never authorize downloads`() =
        runTest {
            val note = audio()
            seed(note)
            val state = members.read(peer)
            members.write(peer, state.copy(retired = mapOf(note.uid.toString() to 2)))
            assertEquals(0, prefetcher.run())
            members.write(peer, state.copy(expected = emptyMap()))
            assertEquals(0, prefetcher.run())
            assertTrue(downloaded.isEmpty())
        }

    @Test fun `window removal and pending capture invalidate the download guard`() =
        runTest {
            val note = audio()
            seed(note)
            during = { guard ->
                pending += note.uid.toString()
                assertFalse(guard())
                pending.clear()
                members.write(peer, WindowMembership())
                assertFalse(guard())
            }
            prefetcher.run()
            assertEquals(1, downloaded.size)
        }

    @Test fun `same version local edits do not authorize phone prefetch`() =
        runTest {
            val note = audio()
            seed(note)
            stored[note.uid] = note.copy(caption = "watch edit")
            assertEquals(0, prefetcher.run())
            assertTrue(downloaded.isEmpty())
        }

    @Test fun `cancellation propagates without starting another note`() =
        runTest {
            seed(audio(1), audio(2))
            during = { throw CancellationException("Constraint lost") }
            assertFailsWith<CancellationException> { prefetcher.run() }
            assertTrue(downloaded.isEmpty())
        }
}
