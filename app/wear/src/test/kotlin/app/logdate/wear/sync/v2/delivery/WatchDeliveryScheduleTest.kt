package app.logdate.wear.sync.v2.delivery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.link.LinkWire
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WatchDeliveryScheduleTest {
    private var active = true
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val peers = mockk<WatchDeliveryPeer>()
    private val outbox = mockk<SyncMetadataService>()
    private val notes = mockk<JournalNotesRepository>()
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val pending =
        PendingUpload(
            Uuid.random().toString(),
            PendingOperation.CREATE,
            scope = UploadScope("local", "https://cloud.test"),
            operationId = "operation",
        )
    private val runner =
        WatchNoteDelivery(
            { active },
            peers,
            outbox,
            notes,
            ledger,
            { mockk() },
            { _, _ -> error("Routing is not scheduling") },
            WatchReceiptApplier(ledger, peers),
            ReleasedToCloud(ledger, peers, outbox),
            mockk(),
            clock =
                object : Clock {
                    override fun now() = Instant.fromEpochMilliseconds(10_000)
                },
        )

    init {
        coEvery { notes.getNoteById(any()) } returns
            JournalNote.Text(
                content = "Capture",
                creationTimestamp = Instant.fromEpochMilliseconds(1),
                lastUpdated = Instant.fromEpochMilliseconds(5),
            )
        coEvery { peers.current() } returns peer
        coEvery { peers.isCurrent(peer) } returns true
        coEvery { outbox.getPendingUploads(EntityType.NOTE) } returns listOf(pending)
    }

    @Test
    fun `a fresh operation without ledger history is due now`() =
        runTest {
            assertEquals(NoteSchedule.Ledger(10_000), runner.schedule())
        }

    @Test
    fun `a current operation follows its stored receipt deadline`() =
        runTest {
            val entry = ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, "${pending.entityId}@operation", 5)
            ledger.recordAttempt(entry, 20_000)
            assertEquals(NoteSchedule.Ledger(20_000), runner.schedule())
        }

    @Test
    fun `obsolete operation deadlines never create a busy loop`() =
        runTest {
            val old = ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, "${pending.entityId}@old-operation", 5)
            ledger.recordAttempt(old, 1)
            coEvery { outbox.getPendingUploads(EntityType.NOTE) } returns emptyList()
            assertEquals(NoteSchedule.Ledger(null), runner.schedule())
        }

    @Test
    fun `a missing local note does not start an immediate retry loop`() =
        runTest {
            coEvery { notes.getNoteById(any()) } returns null
            assertEquals(NoteSchedule.Ledger(null), runner.schedule())
        }

    @Test
    fun `a persisted backup receipt wakes settlement after a restart`() =
        runTest {
            val entry = ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, "${pending.entityId}@operation", 5)
            ledger.applyReceipt(entry.scope, entry.direction, entry.entityType, entry.entityId, 5, DeliveryState.BACKED_UP)
            assertEquals(NoteSchedule.Ledger(10_000), runner.schedule())
        }

    @Test
    fun `an inactive runner leaves scheduling to the legacy outbox`() =
        runTest {
            active = false
            assertEquals(NoteSchedule.Outbox, runner.schedule())
        }
}
