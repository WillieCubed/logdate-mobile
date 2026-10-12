package app.logdate.client.e2e

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.data.journals.OfflineFirstJournalContentRepository
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import app.logdate.client.sync.v2.receipts.StaleWatchCapture
import app.logdate.client.sync.v2.receipts.WatchCaptureNotes
import app.logdate.client.sync.v2.receipts.WatchCaptureProvenance
import app.logdate.client.sync.v2.receipts.WatchCaptureRemovals
import app.logdate.client.sync.v2.receipts.WatchReceiptNotifier
import app.logdate.shared.config.DefaultLogDateConfigRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Real note storage and outbox in an isolated database; run only on Managed Devices or emulators. */
@RunWith(AndroidJUnit4::class)
class WatchCaptureTransactionTest {
    private lateinit var database: LogDateDatabase
    private lateinit var outbox: SyncMetadataService
    private lateinit var repository: SyncableJournalNotesRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var current = true
    private var backups = 0
    private val note =
        JournalNote.Text(
            content = "Recorded on the watch",
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
        )

    @Before
    fun prepare() {
        database =
            getRoomDatabase(
                Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LogDateDatabase::class.java),
                driver = null,
            )
        val owner =
            object : CanonicalOwnerProvider {
                override suspend fun getCanonicalOwnerId() = "isolated-watch-capture-owner"

                override suspend fun hasBoundOwner() = false
            }
        outbox =
            DatabaseSyncMetadataService(
                database.syncMetadataDao(),
                DefaultLogDateConfigRepository(initialBackendUrl = "https://capture-device.example"),
                owner,
            )
        repository =
            OfflineFirstJournalNotesRepository(
                textNoteDao = database.textNoteDao(),
                imageNoteDao = database.imageNoteDao(),
                audioNoteDao = database.audioNoteDao(),
                videoNoteDao = database.videoNoteDao(),
                journalContentDao = database.journalContentDao(),
                journalRepository = mockk<JournalRepository>(),
                mediaCaptionDao = database.mediaCaptionDao(),
                transactionManager = RoomSyncTransactionManager(database),
                syncMetadataService = outbox,
                syncScope = scope,
            )
    }

    @After
    fun close() {
        scope.cancel()
        if (::database.isInitialized) database.close()
    }

    @Test
    fun captureCommitsNoteAndCreateQueueTogether() =
        runBlocking {
            writer().create(note)
            assertEquals(note, repository.getNoteById(note.uid))
            assertEquals(PendingOperation.CREATE, outbox.getPendingUploads(EntityType.NOTE).single().operation)
        }

    @Test
    fun linkChangeAfterNestedCreateRollsBackNoteAndQueue() =
        runBlocking {
            val interrupted =
                object : SyncableJournalNotesRepository by repository {
                    override suspend fun create(note: JournalNote) = repository.create(note).also { current = false }
                }
            assertFailsWith<StaleWatchCapture> { writer(interrupted).create(note) }
            assertEquals(null, repository.getNoteById(note.uid))
            assertTrue(database.syncMetadataDao().getAllPending().isEmpty())
            assertTrue(database.peerDeliveryDao().all(WatchCaptureProvenance.SCOPE, "FROM_WATCH").isEmpty())
        }

    @Test
    fun linkChangeAfterUpdateQueueingRollsBackBothWrites() =
        runBlocking {
            val original = note.copy(content = "Original", lastUpdated = Instant.fromEpochMilliseconds(3), syncVersion = 7)
            repository.createFromSync(original)
            val interrupted =
                object : SyncMetadataService by outbox {
                    override suspend fun enqueuePending(
                        entityId: String,
                        entityType: EntityType,
                        operation: PendingOperation,
                    ) {
                        outbox.enqueuePending(entityId, entityType, operation)
                        current = false
                    }
                }
            assertFailsWith<StaleWatchCapture> { writer(metadata = interrupted).createFromSync(note) }
            assertEquals(original, repository.getNoteById(note.uid))
            assertTrue(database.syncMetadataDao().getAllPending().isEmpty())
            assertTrue(database.peerDeliveryDao().all(WatchCaptureProvenance.SCOPE, "FROM_WATCH").isEmpty())
            assertEquals(0, backups)
        }

    @Test
    fun newerWatchEditKeepsServerVersionAndQueuesBackup() =
        runBlocking {
            repository.createFromSync(note.copy(content = "Original", lastUpdated = Instant.fromEpochMilliseconds(3), syncVersion = 7))
            writer().createFromSync(note)
            assertEquals(note.copy(syncVersion = 7), repository.getNoteById(note.uid))
            assertEquals(PendingOperation.UPDATE, outbox.getPendingUploads(EntityType.NOTE).single().operation)
            assertEquals(1, backups)
        }

    @Test
    fun concurrentCaptureOriginsRetainTheNewestVersion() =
        runBlocking {
            val provenance = WatchCaptureProvenance(database.peerDeliveryDao())
            coroutineScope {
                listOf(9L, 3L, 7L, 1L, 5L).forEach { version ->
                    launch(Dispatchers.Default) { provenance.remember(note.copy(lastUpdated = Instant.fromEpochMilliseconds(version))) }
                }
            }
            assertEquals(
                9L,
                database.peerDeliveryDao().get(WatchCaptureProvenance.SCOPE, "FROM_WATCH", "NOTE", note.uid.toString())?.contentVersion,
            )
        }

    @Test
    fun captureCanQueueBackupWhileSyncWaitsForItsDatabaseTransaction() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val realDao = database.syncMetadataDao()
            val observedDao =
                object : SyncMetadataDao by realDao {
                    override suspend fun getPendingByType(
                        ownerId: String,
                        serverOrigin: String,
                        entityType: String,
                    ): List<PendingUploadEntity> {
                        started.complete(Unit)
                        return realDao.getPendingByType(ownerId, serverOrigin, entityType)
                    }
                }
            val metadata =
                DatabaseSyncMetadataService(
                    observedDao,
                    DefaultLogDateConfigRepository(initialBackendUrl = "https://capture-device.example"),
                    object : CanonicalOwnerProvider {
                        override suspend fun getCanonicalOwnerId() = "isolated-watch-capture-owner"

                        override suspend fun hasBoundOwner() = false
                    },
                )
            var read: kotlinx.coroutines.Deferred<*>? = null
            try {
                withTimeout(5_000) {
                    RoomSyncTransactionManager(database).withTransaction {
                        database.textNoteDao().getAll()
                        read = scope.async { metadata.getPendingUploads(EntityType.NOTE) }
                        started.await()
                        metadata.enqueuePending(note.uid.toString(), EntityType.NOTE, PendingOperation.CREATE)
                    }
                    read?.await()
                }
                assertEquals(PendingOperation.CREATE, metadata.getPendingUploads(EntityType.NOTE).single().operation)
            } finally {
                read?.cancel()
            }
        }

    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)

    @Test
    fun removalCommitsNoteDeleteQueueAndBarrierTogether() =
        runBlocking {
            repository.createFromSync(note)
            val removals = remover()
            removals.remove(peer, removalEnvelope())
            assertEquals(null, repository.getNoteById(note.uid))
            assertEquals(PendingOperation.DELETE, outbox.getPendingUploads(EntityType.NOTE).single().operation)
            assertTrue(removals.isRemoved(peer, note.uid))
            val guarded =
                WatchCaptureNotes(
                    repository,
                    RoomSyncTransactionManager(database),
                    outbox,
                    { true },
                    { },
                    { },
                    canStore = { !removals.isRemoved(peer, it) },
                )
            guarded.createFromSync(note)
            assertEquals(null, repository.getNoteById(note.uid))
        }

    @Test
    fun linkChangeAfterRemovalRollsBackNoteQueueAndBarrier() =
        runBlocking {
            repository.createFromSync(note)
            val interrupted =
                object : SyncableJournalNotesRepository by repository {
                    override suspend fun deleteFromSync(noteId: kotlin.uuid.Uuid) {
                        repository.deleteFromSync(noteId)
                        current = false
                    }
                }
            val removals = remover(interrupted)
            assertFailsWith<StaleWatchCapture> { removals.remove(peer, removalEnvelope()) }
            assertEquals(note, repository.getNoteById(note.uid))
            assertTrue(database.syncMetadataDao().getAllPending().isEmpty())
            kotlin.test.assertFalse(removals.isRemoved(peer, note.uid))
        }

    @Test
    fun removalCanCommitWhileAnotherLedgerWriterWaitsForItsTransaction() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val dao = database.peerDeliveryDao()
            val observed =
                object : PeerDeliveryDao by dao {
                    override suspend fun get(
                        peerScope: String,
                        direction: String,
                        entityType: String,
                        entityId: String,
                    ): PeerDeliveryEntity? {
                        started.complete(Unit)
                        return dao.get(peerScope, direction, entityType, entityId)
                    }
                }
            val ledger = PeerDeliveryLedger(observed)
            var competing: kotlinx.coroutines.Deferred<*>? = null
            try {
                withTimeout(5_000) {
                    RoomSyncTransactionManager(database).withTransaction {
                        database.textNoteDao().getAll()
                        competing =
                            scope.async { ledger.track(peer.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, "competing", 1) }
                        started.await()
                        remover(dao = observed).remove(peer, removalEnvelope())
                    }
                    competing?.await()
                }
                assertTrue(remover().isRemoved(peer, note.uid))
            } finally {
                competing?.cancel()
            }
        }

    private fun removalEnvelope() =
        RecordEnvelope(
            kind = RecordKind.NOTE,
            id = note.uid.toString(),
            contentVersion = 0,
            origin = RecordOrigin.WATCH,
            context = peer,
            operationId = "remove-operation",
            removed = true,
        )

    private fun remover(
        delegate: SyncableJournalNotesRepository = repository,
        dao: PeerDeliveryDao = database.peerDeliveryDao(),
    ): WatchCaptureRemovals {
        val peers = mockk<PhoneDeliveryPeers>()
        coEvery { peers.isCurrent(peer) } answers { current }
        val journals =
            OfflineFirstJournalContentRepository(
                database.journalContentDao(),
                mockk(),
                delegate,
                outbox,
                RoomSyncTransactionManager(database),
            )
        return WatchCaptureRemovals(
            delegate,
            RoomSyncTransactionManager(database),
            dao,
            peers,
            mockk<WatchReceiptNotifier>(relaxed = true),
            journals,
            outbox,
        )
    }

    private fun writer(
        delegate: SyncableJournalNotesRepository = repository,
        metadata: SyncMetadataService = outbox,
    ) = WatchCaptureNotes(delegate, RoomSyncTransactionManager(database), metadata, {
        current
    }, { backups++ }, WatchCaptureProvenance(database.peerDeliveryDao())::remember)
}
