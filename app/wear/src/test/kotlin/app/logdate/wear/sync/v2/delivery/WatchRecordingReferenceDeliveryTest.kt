package app.logdate.wear.sync.v2.delivery

import app.logdate.client.media.storage.LocalMediaRef
import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.HoldReason
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.link.LinkWire
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WatchRecordingReferenceDeliveryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `owned recording is hashed and transferred from the same local file`() =
        runTest {
            val file = temporary.newFile("capture.m4a").apply { writeText("abc") }
            val capture = Fixture(LocalMediaRef(MediaCollection.Recordings, file.name).toString())
            assertTrue(assertNotNull(capture.delivery.deliverIfActive()).success)
            val envelope = (EnvelopeDataMap.fromDataMap(capture.transport.records.single()) as Decoded.Supported).value
            assertEquals(3L, envelope.media.single().sizeBytes)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", envelope.media.single().sha256)
            assertEquals(listOf(file.absolutePath), capture.transport.files)
            assertEquals(listOf("abc"), capture.transport.contents)
            assertEquals(DeliveryState.SENT, capture.row()?.state)
        }

    @Test
    fun `unresolvable recording remains queued without sending metadata or a file`() =
        runTest {
            val capture = Fixture("https://media.test/capture.m4a")
            assertFalse(assertNotNull(capture.delivery.deliverIfActive()).success)
            assertTrue(capture.transport.records.isEmpty())
            assertTrue(capture.transport.files.isEmpty())
            assertEquals(HoldReason.SEND_FAILED, capture.row()?.holdReason)
            assertEquals(listOf(capture.pending), capture.outbox.getPendingUploads(EntityType.NOTE))
        }

    @Test
    fun `missing owned recording remains queued without sending incomplete metadata`() =
        runTest {
            val capture = Fixture(LocalMediaRef(MediaCollection.Recordings, "missing.m4a").toString())
            assertFalse(assertNotNull(capture.delivery.deliverIfActive()).success)
            assertTrue(capture.transport.records.isEmpty())
            assertTrue(capture.transport.files.isEmpty())
            assertEquals(HoldReason.SEND_FAILED, capture.row()?.holdReason)
        }

    private inner class Fixture(
        reference: String,
    ) {
        val peer =
            PeerDeliveryContext(
                LinkWire.encodeKey(ByteArray(32) { 1 }),
                "phone",
                "watch",
                LinkMode.CLOUD,
                "account",
                "https://cloud.test",
            )
        val note =
            JournalNote.Audio(
                mediaRef = reference,
                creationTimestamp = Instant.fromEpochMilliseconds(1),
                lastUpdated = Instant.fromEpochMilliseconds(5),
            )
        val pending =
            PendingUpload(
                note.uid.toString(),
                PendingOperation.CREATE,
                scope = UploadScope("account", "https://cloud.test"),
                operationId = "capture",
            )
        val outbox = mockk<SyncMetadataService>(relaxed = true)
        private val notes = mockk<JournalNotesRepository>()
        private val peers = mockk<WatchDeliveryPeer>()
        private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
        val transport = RecordingTransport()
        private val resolver =
            MediaFileResolver(
                object : MediaDirectories {
                    override fun directory(collection: MediaCollection) = temporary.root.absolutePath
                },
            )
        val delivery: WatchNoteDelivery

        init {
            coEvery { peers.current() } returns peer
            coEvery { peers.isCurrent(peer) } returns true
            coEvery { outbox.getPendingUploads(EntityType.NOTE) } returns listOf(pending)
            coEvery { outbox.isCurrentOperation(EntityType.NOTE, pending) } returns true
            coEvery { notes.getNoteById(note.uid) } returns note
            delivery =
                WatchNoteDelivery(
                    active = { true },
                    peers = peers,
                    outbox = outbox,
                    notes = notes,
                    ledger = ledger,
                    transportFor = { transport },
                    conditions = {
                        _,
                        _,
                        ->
                        RouteConditions(PhoneReach.NEARBY, CloudPresence.SIGNED_IN, true, true, WatchNetwork.UNMETERED, false)
                    },
                    receipts = WatchReceiptApplier(ledger, peers),
                    released = ReleasedToCloud(ledger, peers, outbox),
                    cloud = CloudRelease { SyncResult(success = true) },
                    mediaFilePath = resolver::filePath,
                )
        }

        suspend fun row() = ledger.get(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, "${note.uid}@capture")
    }

    private class RecordingTransport : DataLayerTransportV2 {
        val records = mutableListOf<Map<String, String>>()
        val files = mutableListOf<String>()
        val contents = mutableListOf<String>()

        override suspend fun reach() = PhoneReach.NEARBY

        override suspend fun put(
            path: String,
            data: Map<String, String>,
        ): Boolean {
            records += data
            return true
        }

        override suspend fun deleteOwn(path: String) = true

        override suspend fun sendFile(
            channelPath: String,
            localFilePath: String,
        ): Boolean {
            files += localFilePath
            contents += File(localFilePath).readText()
            return true
        }

        override suspend fun request(
            path: String,
            payload: ByteArray,
        ): Result<ByteArray> = Result.failure(IllegalStateException("No receipt yet"))
    }
}
