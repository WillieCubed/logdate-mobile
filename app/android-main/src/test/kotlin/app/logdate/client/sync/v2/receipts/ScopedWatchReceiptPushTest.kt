package app.logdate.client.sync.v2.receipts

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ScopedWatchReceiptPushTest {
    private val notes = mockk<JournalNotesRepository>()
    private val outbox = mockk<SyncMetadataService>()
    private val peers = mockk<PhoneDeliveryPeers>()
    private val sessions = FakeSessions(OriginBoundSession("https://logdate.test", UserSession("a", "r", "account")))
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val context =
        PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.CLOUD, "account", "https://logdate.test")
    private var current: PeerDeliveryContext? = context
    private var active = true
    private var delivered = true
    private var afterSend: suspend () -> Unit = {}
    private val sent = mutableListOf<Pair<PeerDeliveryContext, ReceiptsResponse>>()
    private val book = PhoneReceiptBook(notes, outbox, sessions, ledger, peers)
    private val notifier =
        WatchReceiptNotifier(
            book,
            ledger,
            WatchPeerMessageSender { peer, path, data ->
                assertEquals(WearPaths.RECEIPTS_PUSH, path)
                sent += peer to (WearJson.decode(data, 1, ReceiptsResponse.serializer()) as Decoded.Supported).value
                afterSend()
                delivered
            },
            { active },
            peers,
        )
    private val id = Uuid.random()

    init {
        coEvery { peers.isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == current }
        coEvery { peers.all() } answers { listOfNotNull(current) }
        stored()
    }

    @Test
    fun `push identifies and targets only the capture's current link`() =
        runTest {
            received()
            assertTrue(notifier.push(context, id.toString(), 5))
            assertEquals(context, sent.single().first)
            assertEquals(context, sent.single().second.context)
            assertEquals(CloudPresence.SIGNED_IN, sent.single().second.cloud)
            assertEquals(
                ReceiptState.BACKED_UP,
                sent
                    .single()
                    .second.receipts
                    .single()
                    .state,
            )
        }

    @Test
    fun `unreceived notes are not pushed to a linked watch`() =
        runTest {
            assertFalse(notifier.push(context, id.toString(), 5))
            assertTrue(sent.isEmpty())
            coVerify(exactly = 0) { notes.getNoteById(any()) }
        }

    @Test
    fun `relinking invalidates old push and rejection audiences`() =
        runTest {
            received()
            current = context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertFalse(notifier.push(context, id.toString(), 5))
            assertFalse(notifier.reject(context, RecordKind.MOMENT, id.toString()))
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `a rejection names its authenticated audience`() =
        runTest {
            assertTrue(notifier.reject(context, RecordKind.MOMENT, id.toString()))
            val response = sent.single().second
            assertEquals(context, response.context)
            assertEquals(ReceiptState.REJECTED_UNSUPPORTED, response.receipts.single().state)
            assertEquals(Long.MAX_VALUE, response.receipts.single().contentVersion)
        }

    @Test
    fun `gate changes during lookup prevent pushes`() =
        runTest {
            received()
            coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } answers {
                active = false
                false
            }
            assertFalse(notifier.push(context, id.toString(), 5))
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `link changes during lookup prevent pushes`() =
        runTest {
            received()
            coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } answers {
                current = null
                false
            }
            assertFalse(notifier.push(context, id.toString(), 5))
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `backup sweep targets each link independently and retains history`() =
        runTest {
            received()
            assertEquals(1, notifier.pushBackedUp())
            assertEquals(0, notifier.pushBackedUp())
            assertEquals(context, sent.single().second.context)
            assertEquals(DeliveryState.BACKED_UP, entry()?.state)
        }

    @Test
    fun `failed delivery is retried by the next backup sweep`() =
        runTest {
            received()
            delivered = false
            assertEquals(0, notifier.pushBackedUp())
            assertEquals(DeliveryState.ON_PHONE, entry()?.state)
            delivered = true
            assertEquals(1, notifier.pushBackedUp())
            assertEquals(DeliveryState.BACKED_UP, entry()?.state)
        }

    @Test
    fun `a relink during send cannot settle the old delivery history`() =
        runTest {
            received()
            afterSend = { current = null }
            assertEquals(0, notifier.pushBackedUp())
            assertEquals(DeliveryState.ON_PHONE, entry()?.state)
        }

    @Test
    fun `a fresh link does not sweep the old link's records`() =
        runTest {
            received()
            current = context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertEquals(0, notifier.pushBackedUp())
            assertTrue(sent.isEmpty())
            assertEquals(DeliveryState.ON_PHONE, entry()?.state)
        }

    @Test
    fun `cancellation does not settle pending pushes`() =
        runTest {
            received()
            afterSend = { throw CancellationException("Stopped") }
            assertFailsWith<CancellationException> { notifier.pushBackedUp() }
            assertEquals(DeliveryState.ON_PHONE, entry()?.state)
        }

    @Test
    fun `a scoped acknowledgement records only its link before pushing`() =
        runTest {
            ReceiptAcknowledger(ledger, notifier, context).acknowledge(id, Instant.fromEpochMilliseconds(5))
            assertEquals(DeliveryState.ON_PHONE, entry()?.state)
            assertEquals(context, sent.single().second.context)
        }

    @Test
    fun `an unbound acknowledgement creates no delivery proof`() =
        runTest {
            ReceiptAcknowledger(ledger, notifier).acknowledge(id, Instant.fromEpochMilliseconds(5))
            assertTrue(sent.isEmpty())
            assertEquals(null, entry())
            assertEquals(
                null,
                ledger.get(
                    app.logdate.client.sync.peer.delivery.PeerScopes.WATCHES,
                    DeliveryDirection.FROM_WATCH,
                    EntityType.NOTE,
                    id.toString(),
                ),
            )
        }

    @Test
    fun `a stale scoped acknowledgement cannot record or push`() =
        runTest {
            current = null
            ReceiptAcknowledger(ledger, notifier, context).acknowledge(id, Instant.fromEpochMilliseconds(5))
            assertTrue(sent.isEmpty())
            assertEquals(null, entry())
        }

    @Test
    fun `an older acknowledgement cannot advance a newer delivery`() =
        runTest {
            ledger.track(context.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString(), 6)
            ReceiptAcknowledger(ledger, notifier, context).acknowledge(id, Instant.fromEpochMilliseconds(5))
            assertEquals(6L, entry()?.contentVersion)
            assertEquals(DeliveryState.NEW, entry()?.state)
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `two linked watches receive separate responses and histories`() =
        runTest {
            val other = context.copy(watchNode = "other-watch", linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            coEvery { peers.isCurrent(other) } returns true
            coEvery { peers.all() } returns listOf(context, other)
            received()
            val otherEntry = ledger.track(other.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString(), 5)
            ledger.moveTo(otherEntry, DeliveryState.ON_PHONE)
            assertEquals(2, notifier.pushBackedUp())
            assertEquals(listOf(context, other), sent.map { it.first })
            assertTrue(sent.all { it.first == it.second.context })
        }

    @Test
    fun `a newer version arriving during a send stays pending`() =
        runTest {
            received()
            afterSend = { ledger.track(context.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString(), 6) }
            notifier.pushBackedUp()
            assertEquals(6L, entry()?.contentVersion)
            assertEquals(DeliveryState.NEW, entry()?.state)
        }

    @Test
    fun `backup notifications retain operation identity and the public note ID`() =
        runTest {
            val entry = ledger.track(context.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, "$id@operation", 5)
            ledger.moveTo(entry, DeliveryState.ON_PHONE)
            assertEquals(1, notifier.pushBackedUp())
            val receipt =
                sent
                    .single()
                    .second.receipts
                    .single()
            assertEquals(id.toString(), receipt.id)
            assertEquals("operation", receipt.operationId)
            assertEquals(ReceiptState.BACKED_UP, receipt.state)
        }

    private suspend fun received() {
        val entry = ledger.track(context.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString(), 5)
        ledger.moveTo(entry, DeliveryState.ON_PHONE)
    }

    private suspend fun entry() = ledger.get(context.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, id.toString())

    private fun stored() {
        coEvery { notes.getNoteById(id) } returns
            JournalNote.Text(
                uid = id,
                creationTimestamp = Instant.fromEpochMilliseconds(1),
                lastUpdated = Instant.fromEpochMilliseconds(5),
                content = "watch",
                syncVersion = 7,
            )
        coEvery { outbox.hasPending(EntityType.NOTE, id.toString()) } returns false
    }
}
