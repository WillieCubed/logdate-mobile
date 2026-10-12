package app.logdate.client.sync.v2.receipts

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.WatchNoteAcknowledger
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.client.sync.peer.link.LinkWire
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScopedCaptureIngestionTest {
    private val peers = mockk<PhoneDeliveryPeers>()
    private val notes = mockk<JournalNotesRepository>()
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var current: PeerDeliveryContext? = peer
    private var afterCreate: () -> Unit = {}
    private var beforeStore: suspend () -> Unit = {}
    private var created = 0
    private val stored = mutableListOf<PeerDeliveryContext>()
    private val audio = mutableListOf<Pair<PeerDeliveryContext, List<Byte>>>()
    private val rejected = mutableListOf<String>()
    private val note =
        JournalNote.Text(
            content = "watch",
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
        )
    private val path = WearPaths.watchRecord(RecordKind.NOTE, note.uid)
    private val ingestion =
        ScopedCaptureIngestion(peers) { context, _ ->
            created++
            afterCreate()
            WatchRecordIngestorV2(
                ingestMetadata = {
                    beforeStore()
                    stored += context
                },
                ingestAudio = { _, input -> audio += context to input.readBytes().toList() },
                notes = notes,
                acknowledger = WatchNoteAcknowledger { _, _ -> },
                rejection = RecordRejection { _, id -> rejected += id },
            )
        }

    init {
        coEvery { peers.current(any()) } answers { current?.takeIf { it.watchNode == firstArg<String>() } }
        coEvery { peers.isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == current }
        coEvery { notes.getNoteById(any()) } returns note
    }

    @Test
    fun `a current source and matching envelope reach that link's ingestor`() =
        runTest {
            entryPoint().onRecord("watch", path, data(peer))
            assertEquals(listOf(peer), stored)
        }

    @Test
    fun `a different source cannot claim the envelope's watch`() =
        runTest {
            ingestion.onRecord("another-watch", path, data(peer))
            assertTrue(stored.isEmpty())
            assertEquals(0, created)
        }

    @Test
    fun `a missing source cannot ingest or reject a record`() =
        runTest {
            ingestion.onRecord(null, path, data(peer))
            assertTrue(stored.isEmpty())
            assertTrue(rejected.isEmpty())
            assertEquals(0, created)
        }

    @Test
    fun `a replaced link cannot deliver into the new link`() =
        runTest {
            current = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            ingestion.onRecord("watch", path, data(peer))
            assertTrue(stored.isEmpty())
            assertTrue(rejected.isEmpty())
            assertEquals(0, created)
        }

    @Test
    fun `an envelope without verified context is ignored`() =
        runTest {
            ingestion.onRecord("watch", path, data(null))
            assertTrue(stored.isEmpty())
            assertEquals(0, created)
        }

    @Test
    fun `only the matching source and link can transfer audio`() =
        runTest {
            entryPoint().onMedia("watch", WearPaths.recordMedia(RecordKind.NOTE, note.uid, peer), ByteArrayInputStream(byteArrayOf(1, 2)))
            assertEquals(listOf(peer to listOf<Byte>(1, 2)), audio)
        }

    @Test
    fun `unscoped and stale-link audio are never consumed`() =
        runTest {
            ingestion.onMedia("watch", WearPaths.recordMedia(RecordKind.NOTE, note.uid), ByteArrayInputStream(byteArrayOf(1)))
            current = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            ingestion.onMedia("watch", WearPaths.recordMedia(RecordKind.NOTE, note.uid, peer), ByteArrayInputStream(byteArrayOf(2)))
            assertTrue(audio.isEmpty())
            assertEquals(0, created)
        }

    @Test
    fun `a link change during ingestor creation prevents consumption`() =
        runTest {
            afterCreate = { current = null }
            ingestion.onRecord("watch", path, data(peer))
            assertTrue(stored.isEmpty())
        }

    @Test
    fun `overlapping events share an ingestor and release it when finished`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            beforeStore = {
                entered.complete(Unit)
                release.await()
            }
            val first = async { ingestion.onRecord("watch", path, data(peer)) }
            runCurrent()
            assertTrue(entered.isCompleted)
            val second = async { ingestion.onRecord("watch", path, data(peer)) }
            runCurrent()
            assertEquals(1, created)
            release.complete(Unit)
            first.await()
            second.await()
            beforeStore = {}
            ingestion.onRecord("watch", path, data(peer))
            assertEquals(2, created)
            assertEquals(3, stored.size)
        }

    @Test
    fun `cancellation releases the ingestor and propagates to its caller`() =
        runTest {
            beforeStore = { throw CancellationException("stopped") }
            assertFailsWith<CancellationException> { ingestion.onRecord("watch", path, data(peer)) }
            beforeStore = {}
            ingestion.onRecord("watch", path, data(peer))
            assertEquals(2, created)
            assertEquals(listOf(peer), stored)
        }

    private fun entryPoint() =
        WatchRecordIngestorV2(
            ingestMetadata = { error("source validation was bypassed") },
            ingestAudio = { _, _ -> error("source validation was bypassed") },
            notes = notes,
            acknowledger = WatchNoteAcknowledger { _, _ -> error("unbound acknowledgement") },
            rejection = RecordRejection { _, _ -> error("unbound rejection") },
            scoped = ingestion,
        )

    private fun data(context: PeerDeliveryContext?) =
        EnvelopeDataMap.toDataMap(
            NoteEnvelopeBody.envelope(note, RecordOrigin.WATCH, DeliveryPriority.URGENT).copy(context = context),
            sentAt = 1,
        )
}
