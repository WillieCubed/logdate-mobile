package app.logdate.client.e2e

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.data.moments.OfflineFirstMarkedMomentRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.MomentEnvelopeBody
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.receipts.MomentReceiptBook
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import app.logdate.client.sync.v2.receipts.PhoneReceiptBook
import app.logdate.client.sync.v2.receipts.StaleWatchCapture
import app.logdate.client.sync.v2.receipts.WatchMomentIngestor
import app.logdate.client.sync.v2.receipts.WatchPeerMessageSender
import app.logdate.client.sync.v2.receipts.WatchReceiptNotifier
import app.logdate.shared.config.DefaultLogDateConfigRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class WatchMomentTransactionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "isolated-watch-moment-${Uuid.random()}.db"
    private lateinit var db: LogDateDatabase
    private lateinit var outbox: DatabaseSyncMetadataService
    private lateinit var repository: SyncableMarkedMomentRepository
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val at = Instant.fromEpochMilliseconds(100)
    private val mark = MarkedMoment(Uuid.random(), at, "UTC", null, 72, MarkState.OPEN, "", emptySet(), "watch-device", at, 0)
    private var current = true

    @Before fun prepare() {
        db =
            getRoomDatabase(
                Room.databaseBuilder(context, LogDateDatabase::class.java, databaseName),
                driver = null,
            )
        val owner =
            object : CanonicalOwnerProvider {
                override suspend fun getCanonicalOwnerId() = "isolated-watch-moment-owner"

                override suspend fun hasBoundOwner() = false
            }
        outbox =
            DatabaseSyncMetadataService(
                db.syncMetadataDao(),
                DefaultLogDateConfigRepository(initialBackendUrl = "https://capture-device.example"),
                owner,
            )
        repository = OfflineFirstMarkedMomentRepository(db.markedMomentDao(), RoomSyncTransactionManager(db), outbox)
    }

    @After fun close() {
        db.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun captureRowOutboxAndOperationProofCommitTogether() =
        runBlocking {
            send(writer())
            assertEquals(mark, repository.getMark(mark.id))
            assertEquals(PendingOperation.CREATE, outbox.getPendingUploads(EntityType.MOMENT).single().operation)
            assertEquals(
                "ON_PHONE",
                db
                    .peerDeliveryDao()
                    .all(peer.ledgerScope, "FROM_WATCH")
                    .single()
                    .state,
            )
        }

    @Test fun interruptionAfterReceiptWriteRollsBackAllThreeStores() =
        runBlocking {
            val dao = db.peerDeliveryDao()
            val interrupted =
                object : PeerDeliveryDao by dao {
                    override suspend fun retainNewest(entry: PeerDeliveryEntity) {
                        dao.retainNewest(entry)
                        throw CancellationException("interrupted moment receipt")
                    }
                }
            assertFailsWith<CancellationException> { send(writer(deliveries = interrupted)) }
            assertNull(repository.getMark(mark.id))
            assertTrue(db.syncMetadataDao().getAllPending().isEmpty())
            assertTrue(dao.all(peer.ledgerScope, "FROM_WATCH").isEmpty())
        }

    @Test fun revocationDuringMergeRollsBackRowAndOperationWithoutReceipt() =
        runBlocking {
            val switching =
                object : SyncableMarkedMomentRepository by repository {
                    override suspend fun applyFromSync(moment: MarkedMoment): MarkedMoment? =
                        repository.applyFromSync(moment).also {
                            current =
                                false
                        }
                }
            assertFailsWith<StaleWatchCapture> { send(writer(switching)) }
            assertNull(repository.getMark(mark.id))
            assertTrue(db.syncMetadataDao().getAllPending().isEmpty())
            assertTrue(db.peerDeliveryDao().all(peer.ledgerScope, "FROM_WATCH").isEmpty())
        }

    @Test fun deletionBeforeCapturePersistsThroughReplay() =
        runBlocking {
            send(writer(), removed = true)
            assertNotNull(db.markedMomentDao().getById(mark.id)?.deletedAt)
            val pending = outbox.getPendingUploads(EntityType.MOMENT).single()
            assertEquals(PendingOperation.DELETE, pending.operation)
            send(writer())
            assertNull(repository.getMark(mark.id))
            assertEquals(pending, outbox.getPendingUploads(EntityType.MOMENT).single())
        }

    @Test fun failedReceiptPushRemainsPollableAfterDatabaseRestart() =
        runBlocking {
            val peers = mockk<PhoneDeliveryPeers>()
            coEvery { peers.isCurrent(peer) } answers { current }
            val book = MomentReceiptBook(db.markedMomentDao(), db.peerDeliveryDao(), outbox, peers) { true }
            val shared = PhoneReceiptBook(mockk(), outbox, mockk(), PeerDeliveryLedger(db.peerDeliveryDao()), peers, book)
            val notifier =
                WatchReceiptNotifier(
                    shared,
                    PeerDeliveryLedger(db.peerDeliveryDao()),
                    WatchPeerMessageSender {
                        _,
                        _,
                        _,
                        ->
                        false
                    },
                    { true },
                    peers,
                )
            send(writer(notifier = notifier))
            db.close()
            prepare()
            val restarted = MomentReceiptBook(db.markedMomentDao(), db.peerDeliveryDao(), outbox, peers) { true }
            val receipt = assertNotNull(restarted.receiptFor(peer, ReceiptQuery(RecordKind.MOMENT, mark.id.toString(), 100, "capture-1")))
            assertEquals(ReceiptState.ON_PHONE, receipt.state)
            assertEquals(mark, repository.getMark(mark.id))
            assertEquals(PendingOperation.CREATE, outbox.getPendingUploads(EntityType.MOMENT).single().operation)
        }

    private fun writer(
        moments: SyncableMarkedMomentRepository = repository,
        deliveries: PeerDeliveryDao = db.peerDeliveryDao(),
        notifier: WatchReceiptNotifier = mockk(relaxed = true),
    ): WatchMomentIngestor {
        val peers = mockk<PhoneDeliveryPeers>()
        coEvery { peers.current("watch") } answers { peer.takeIf { current } }
        coEvery { peers.isCurrent(peer) } answers { current }
        return WatchMomentIngestor(
            moments,
            db.markedMomentDao(),
            deliveries,
            outbox,
            RoomSyncTransactionManager(db),
            peers,
            notifier,
            { true },
            {},
        )
    }

    private suspend fun send(
        writer: WatchMomentIngestor,
        removed: Boolean = false,
    ) {
        val envelope =
            MomentEnvelopeBody.envelope(mark, RecordOrigin.WATCH).copy(context = peer, operationId = "capture-1").let {
                if (removed) it.copy(removed = true, body = null, contentVersion = 0) else it
            }
        writer.onRecord(
            "watch",
            WearPaths.watchRecord(RecordKind.MOMENT, mark.id, peer, "capture-1"),
            EnvelopeDataMap.toDataMap(envelope, 100),
        )
    }
}
