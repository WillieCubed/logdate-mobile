package app.logdate.client.sync.v2.receipts

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [PhoneReceiptBook]: what the phone has of each watch record, said as a receipt. */
class PhoneReceiptBookTest {
    private val notes = mockk<JournalNotesRepository>()
    private val outbox = mockk<SyncMetadataService>()
    private val sessions = FakeSessions(OriginBoundSession("https://logdate.test", UserSession("access", "refresh", "account")))
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val book = PhoneReceiptBook(notes, outbox, sessions, ledger)
    private val id = Uuid.random()

    @Test
    fun `a stored note not yet backed up is on the phone`() =
        runTest {
            stored(updated = 5, syncVersion = 0, pending = true)

            assertEquals(Receipt(RecordKind.NOTE, id.toString(), 5, ReceiptState.ON_PHONE), book.receiptFor(query(5)))
        }

    @Test
    fun `a backed up note is reported with its server version`() =
        runTest {
            stored(updated = 5, syncVersion = 7, pending = false)

            assertEquals(Receipt(RecordKind.NOTE, id.toString(), 5, ReceiptState.BACKED_UP, serverVersion = 7), book.receiptFor(query(5)))
        }

    @Test
    fun `with no account a stored note stays on the phone`() =
        runTest {
            sessions.bound = null
            stored(updated = 5, syncVersion = 7, pending = false)

            assertEquals(ReceiptState.ON_PHONE, book.receiptFor(query(5))?.state)
            assertEquals(CloudPresence.NO_ACCOUNT, book.cloudPresence())
        }

    @Test
    fun `an older copy on the phone gives no receipt`() =
        runTest {
            stored(updated = 4, syncVersion = 0, pending = true)

            assertNull(book.receiptFor(query(5)))
        }

    @Test
    fun `a note the phone deleted after receiving it is reported deleted`() =
        runTest {
            coEvery { notes.getNoteById(id) } returns null
            ledger.track(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString(), 5)

            assertEquals(ReceiptState.DELETED_ON_PHONE, book.receiptFor(query(5))?.state)
        }

    @Test
    fun `a note the phone never received gives no receipt`() =
        runTest {
            coEvery { notes.getNoteById(id) } returns null

            assertNull(book.receiptFor(query(5)))
        }

    @Test
    fun `a kind this phone does not take this way is rejected for every version`() =
        runTest {
            assertEquals(
                Receipt(RecordKind.MOMENT, id.toString(), PhoneReceiptBook.ANY_VERSION, ReceiptState.REJECTED_UNSUPPORTED),
                book.receiptFor(ReceiptQuery(RecordKind.MOMENT, id.toString(), 5)),
            )
        }

    @Test
    fun `a missing note received at an older version cannot acknowledge a newer watch edit as deleted`() =
        runTest {
            coEvery { notes.getNoteById(id) } returns null
            ledger.track(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString(), 4)
            assertNull(book.receiptFor(query(5)))
        }

    @Test
    fun `a session switch during lookup cannot report another account backup`() =
        runTest {
            stored(updated = 5, syncVersion = 7, pending = false)
            coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } answers {
                sessions.bound = OriginBoundSession("https://other.test", UserSession("b", "r", "other"))
                false
            }
            assertNull(book.receiptFor(query(5)))
        }

    private fun stored(
        updated: Long,
        syncVersion: Long,
        pending: Boolean,
    ) {
        coEvery { notes.getNoteById(id) } returns
            JournalNote.Text(
                uid = id,
                creationTimestamp = Instant.fromEpochMilliseconds(1),
                lastUpdated = Instant.fromEpochMilliseconds(updated),
                content = "from the watch",
                syncVersion = syncVersion,
            )
        coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } returns pending
    }

    private fun query(version: Long) = ReceiptQuery(RecordKind.NOTE, id.toString(), version)
}
