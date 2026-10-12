package app.logdate.client.sync.v2.receipts

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WatchCaptureRemovalsTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val peers = mockk<PhoneDeliveryPeers>()
    private val notes = mockk<SyncableJournalNotesRepository>()
    private val dao = MemoryPeerDeliveryDao()
    private val ledger = PeerDeliveryLedger(dao)
    private val outbox = mockk<SyncMetadataService>()
    private val book = PhoneReceiptBook(notes, outbox, FakeSessions(null), ledger, peers)
    private val receipts = WatchReceiptNotifier(book, ledger, WatchPeerMessageSender { _, _, _ -> true }, { true }, peers)
    private val id = Uuid.random()
    private var stored: JournalNote? = JournalNote.Text(id, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(5), "watch")
    private val transactions =
        object : SyncTransactionManager {
            override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
        }
    private val removals = WatchCaptureRemovals(notes, transactions, dao, peers, receipts, mockk(relaxed = true), outbox)
    private val envelope =
        RecordEnvelope(
            kind = RecordKind.NOTE,
            id = id.toString(),
            contentVersion = 0,
            origin = RecordOrigin.WATCH,
            context = peer,
            operationId = "remove-operation",
            removed = true,
        )

    init {
        coEvery { peers.isCurrent(peer) } returns true
        coEvery { notes.getNoteById(id) } answers { stored }
        coEvery { notes.deleteFromSync(id) } answers { stored = null }
        coEvery { outbox.hasPending(any(), any()) } returns false
        coEvery { outbox.enqueuePending(any(), any(), any()) } returns Unit
    }

    @Test
    fun `a verified removal deletes the phone note and leaves a durable barrier`() =
        runTest {
            removals.remove(peer, envelope)
            assertNull(stored)
            assertTrue(removals.isRemoved(peer, id))
            assertTrue(WatchCaptureRemovals(notes, transactions, dao, peers, receipts, mockk(relaxed = true), outbox).isRemoved(peer, id))
        }

    @Test
    fun `a removal receipt names only the durable removal operation`() =
        runTest {
            removals.remove(peer, envelope)
            val query = ReceiptQuery(RecordKind.NOTE, id.toString(), 0, "remove-operation", removed = true)
            val receipt = book.receiptFor(peer, query)
            assertTrue(receipt?.removed == true)
            kotlin.test.assertEquals(ReceiptState.DELETED_ON_PHONE, receipt.state)
            assertNull(book.receiptFor(peer, query.copy(operationId = "different-operation")))
            assertNull(book.receiptFor(peer, query.copy(removed = false)))
        }

    @Test
    fun `a removal received before its capture still prevents resurrection`() =
        runTest {
            stored = null
            removals.remove(peer, envelope)
            assertTrue(removals.isRemoved(peer, id))
        }

    @Test
    fun `a replacement link cannot reuse an earlier removal barrier`() =
        runTest {
            removals.remove(peer, envelope)
            assertTrue(removals.isRemoved(peer, id))
            assertFalse(removals.isRemoved(peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 })), id))
        }

    @Test
    fun `a stale link cannot remove a note`() =
        runTest {
            coEvery { peers.isCurrent(peer) } returns false
            removals.remove(peer, envelope)
            assertTrue(stored != null)
            assertFalse(removals.isRemoved(peer, id))
        }

    @Test
    fun `a capture envelope cannot be mistaken for a removal`() =
        runTest {
            removals.remove(peer, envelope.copy(removed = false))
            assertTrue(stored != null)
            assertFalse(removals.isRemoved(peer, id))
        }

    @Test
    fun `mismatched context and absent operation identity are refused`() =
        runTest {
            removals.remove(peer, envelope.copy(context = null))
            removals.remove(peer, envelope.copy(operationId = null))
            assertTrue(stored != null)
            assertFalse(removals.isRemoved(peer, id))
        }
}
