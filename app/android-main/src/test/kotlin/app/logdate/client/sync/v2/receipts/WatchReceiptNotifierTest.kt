package app.logdate.client.sync.v2.receipts

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsRequest
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WatchReceiptNotifier], [ReceiptAcknowledger] and [WatchReceiptResponder]: receipts pushed and answered. */
class WatchReceiptNotifierTest {
    private val notes = mockk<JournalNotesRepository>()
    private val outbox = mockk<SyncMetadataService>()
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val sessions = FakeSessions(OriginBoundSession("https://logdate.test", UserSession("a", "r", "account")))
    private val context =
        PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32)), "phone", "watch", LinkMode.CLOUD, "account", "https://logdate.test")
    private val peers =
        mockk<PhoneDeliveryPeers> {
            coEvery { isCurrent(context) } returns true
            coEvery { all() } returns listOf(context)
        }
    private val book = PhoneReceiptBook(notes, outbox, sessions, ledger, peers)
    private val sent = mutableListOf<Pair<String, ByteArray>>()
    private var active = true
    private val notifier =
        WatchReceiptNotifier(
            book = book,
            ledger = ledger,
            sender =
                WatchPeerMessageSender { peer, path, data ->
                    assertEquals(context, peer)
                    sent += path to data
                    true
                },
            active = { active },
            peers = peers,
        )
    private val id = Uuid.random()

    @Test
    fun `a stored watch note is recorded and receipted as on the phone`() =
        runTest {
            stored(syncVersion = 0, pending = true)

            ReceiptAcknowledger(ledger, notifier, context).acknowledge(id, Instant.fromEpochMilliseconds(5))

            val entry = ledger.get(context.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString())
            assertEquals(DeliveryState.ON_PHONE, entry?.state)
            assertEquals(listOf(Receipt(RecordKind.NOTE, id.toString(), 5, ReceiptState.ON_PHONE)), pushedReceipts())
        }

    @Test
    fun `a note backed up since it arrived is pushed once and recorded`() =
        runTest {
            stored(syncVersion = 0, pending = true)
            ReceiptAcknowledger(ledger, notifier, context).acknowledge(id, Instant.fromEpochMilliseconds(5))
            sent.clear()
            stored(syncVersion = 7, pending = false)

            assertEquals(1, notifier.pushBackedUp())
            assertEquals(0, notifier.pushBackedUp())
            assertEquals(listOf(Receipt(RecordKind.NOTE, id.toString(), 5, ReceiptState.BACKED_UP, serverVersion = 7)), pushedReceipts())
        }

    @Test
    fun `nothing is pushed while the watch does not take receipts`() =
        runTest {
            active = false

            notifier.push(context, id.toString(), 5)

            assertTrue(sent.isEmpty())
        }

    @Test
    fun `a rejection covers every version`() =
        runTest {
            notifier.reject(context, RecordKind.MOMENT, id.toString())

            assertEquals(
                listOf(Receipt(RecordKind.MOMENT, id.toString(), PhoneReceiptBook.ANY_VERSION, ReceiptState.REJECTED_UNSUPPORTED)),
                pushedReceipts(),
            )
        }

    @Test
    fun `an unbound request cannot learn account status or note receipts`() =
        runTest {
            stored(syncVersion = 0, pending = true)
            val query = ReceiptsRequest(items = listOf(ReceiptQuery(RecordKind.NOTE, id.toString(), 5)))
            val request = WearJson.encodeToBytes(ReceiptsRequest.serializer(), query)

            val answer = decode(WatchReceiptResponder(book).answer("watch", request))

            assertEquals(CloudPresence.UNKNOWN, answer.cloud)
            assertTrue(answer.receipts.isEmpty())
        }

    @Test
    fun `an unreadable request is answered with no receipts`() =
        runTest {
            assertTrue(decode(WatchReceiptResponder(book).answer("watch", "nope".encodeToByteArray())).receipts.isEmpty())
        }

    @Test
    fun `disabling receipts during lookup prevents the push`() =
        runTest {
            stored(syncVersion = 7, pending = false)
            ledger.track(context.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString(), 5)
            coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } answers {
                active = false
                false
            }
            notifier.push(context, id.toString(), 5)
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `old delivery history is retained so a later deletion can still be receipted`() =
        runTest {
            val oldLedger =
                PeerDeliveryLedger(
                    MemoryPeerDeliveryDao(),
                    object : kotlin.time.Clock {
                        override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
                    },
                )
            val oldBook = PhoneReceiptBook(notes, outbox, sessions, oldLedger, peers)
            val oldNotifier = WatchReceiptNotifier(oldBook, oldLedger, WatchPeerMessageSender { _, _, _ -> true }, { true }, peers)
            stored(syncVersion = 0, pending = true)
            ReceiptAcknowledger(oldLedger, oldNotifier, context).acknowledge(id, Instant.fromEpochMilliseconds(5))
            stored(syncVersion = 7, pending = false)
            oldNotifier.pushBackedUp()
            oldNotifier.pushBackedUp()
            coEvery { notes.getNoteById(id) } returns null
            assertEquals(ReceiptState.DELETED_ON_PHONE, oldBook.receiptFor(context, ReceiptQuery(RecordKind.NOTE, id.toString(), 5))?.state)
        }

    private fun stored(
        syncVersion: Long,
        pending: Boolean,
    ) {
        coEvery { notes.getNoteById(id) } returns
            JournalNote.Text(
                uid = id,
                creationTimestamp = Instant.fromEpochMilliseconds(1),
                lastUpdated = Instant.fromEpochMilliseconds(5),
                content = "from the watch",
                syncVersion = syncVersion,
            )
        coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } returns pending
    }

    private fun pushedReceipts(): List<Receipt> =
        sent.flatMap { (path, data) ->
            assertEquals(WearPaths.RECEIPTS_PUSH, path)
            decode(data).receipts
        }

    private fun decode(bytes: ByteArray): ReceiptsResponse =
        (WearJson.decode(bytes, WearProtocol.RECEIPTS_SCHEMA, ReceiptsResponse.serializer()) as Decoded.Supported).value
}
