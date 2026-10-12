package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.PrefixedKeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMomentRepository
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import app.logdate.client.sync.datalayer.v2.WindowTombstoneDataMap
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.MomentEnvelopeBody
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.wear.di.WatchCloud
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.link.WatchLinkStore
import app.logdate.wear.sync.v2.window.MomentWindowApplier
import app.logdate.wear.sync.v2.window.WindowApplied
import app.logdate.wear.sync.v2.window.WindowApplier
import app.logdate.wear.sync.v2.window.WindowItem
import app.logdate.wear.sync.v2.window.WindowMembershipStore
import app.logdate.wear.v2.mark.MarkedMomentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Real installed Room/store/outbox; the verified remote boundary is controlled, not paired transport. */
@RunWith(AndroidJUnit4::class)
class MomentWindowRoomTest {
    private val koin = KoinJavaComponent.getKoin()
    private val metadata = koin.get<SyncMetadataService>()
    private val repository = koin.get<MarkedMomentRepository>() as SyncableMarkedMomentRepository

    @Test fun returnedResolutionRefreshesTheRoomOperationSoOldReceiptCannotSettleIt() =
        runBlocking {
            val f = fixture()
            val original = pending(f.mark.id)
            assertEquals(WindowApplied(1, 0), f.applier.reconcile(f.peer, f.response, listOf(f.item)))
            val actual = assertNotNull(repository.getMark(f.mark.id))
            assertEquals(MarkState.FILLED, actual.state)
            assertEquals("Phone resolution", actual.caption)
            assertEquals(72, actual.heartRateBpm)
            assertEquals(1, actual.resolvedNoteIds.size)
            assertNotEquals(original.operationId, pending(f.mark.id).operationId)
            assertFalse(metadata.settleIfCurrent(EntityType.MOMENT, original, actual.lastUpdated, 7))
            assertTrue(metadata.hasPending(EntityType.MOMENT, f.mark.id.toString()))
            assertEquals(WindowApplied(1, 0), f.applier.reconcile(f.peer, f.response, listOf(f.item)))
            val current = pending(f.mark.id)
            assertEquals(
                WindowApplied(0, 1),
                f.applier.reconcile(f.peer, f.response.copy(members = emptyList(), revision = 2), emptyList()),
            )
            assertEquals(actual, repository.getMark(f.mark.id))
            assertEquals(current, pending(f.mark.id))
            assertFalse(f.applier.onItem(f.item))
        }

    @Test fun interruptedOutboxReplacementRollsBackTheMergedRoomRowAndOperation() =
        runBlocking {
            val interrupted =
                object : SyncMetadataService by metadata {
                    override suspend fun enqueuePending(
                        entityId: String,
                        entityType: EntityType,
                        operation: PendingOperation,
                    ) {
                        metadata.enqueuePending(entityId, entityType, operation)
                        throw CancellationException("interrupted after operation replacement")
                    }
                }
            val f = fixture(outbox = interrupted)
            val before = repository.getMark(f.mark.id)
            val original = pending(f.mark.id)
            assertFailsWith<CancellationException> { f.applier.reconcile(f.peer, f.response, listOf(f.item)) }
            assertEquals(before, repository.getMark(f.mark.id))
            assertEquals(original, pending(f.mark.id))
            assertTrue(
                f.members
                    .read(f.peer)
                    .entries
                    .isEmpty(),
            )
            assertNull(f.members.read(f.peer).snapshotRevision)
        }

    @Test fun capabilityWithdrawalAfterRoomMergeRollsBackWithoutCompletingTheWindow() =
        runBlocking {
            var supported = true
            val revoked =
                object : SyncableMarkedMomentRepository by repository {
                    override suspend fun applyFromSync(
                        moment: app.logdate.client.repository.moments.MarkedMoment,
                    ): app.logdate.client.repository.moments.MarkedMoment? {
                        val value = repository.applyFromSync(moment)
                        supported = false
                        return value
                    }
                }
            val f = fixture(moments = revoked, accepts = { supported })
            val before = repository.getMark(f.mark.id)
            val original = pending(f.mark.id)
            assertNull(f.applier.reconcile(f.peer, f.response, listOf(f.item)))
            assertEquals(before, repository.getMark(f.mark.id))
            assertEquals(original, pending(f.mark.id))
            assertTrue(
                f.members
                    .read(f.peer)
                    .entries
                    .isEmpty(),
            )
            assertNull(f.members.read(f.peer).snapshotRevision)
        }

    private suspend fun fixture(
        outbox: SyncMetadataService = metadata,
        moments: SyncableMarkedMomentRepository = repository,
        accepts: suspend (PeerDeliveryContext) -> Boolean = { true },
    ): Fixture {
        val at = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val mark =
            MarkedMomentEntity(
                id = Uuid.random(),
                capturedAt = at,
                timeZoneId = "UTC",
                state = MarkedMomentState.OPEN,
                heartRateBpm = 72,
                originDeviceId = "window-watch",
                lastUpdated = at,
            )
        koin.get<MarkedMomentStore>().create(mark)
        val storage = PrefixedKeyValueStorage(koin.get<KeyValueStorage>(), "moment_window_test.${mark.id}.")
        val links =
            WatchLinkStore(
                storage,
                object : PairKeyVault {
                    override suspend fun put(
                        peer: String,
                        key: ByteArray,
                    ) = Unit

                    override suspend fun get(peer: String): ByteArray? = null

                    override suspend fun remove(peer: String) = Unit
                },
            )
        links.savePairing("isolated-phone", ByteArray(32), LinkWire.encodeKey(ByteArray(32) { 1 }))
        val peers = WatchDeliveryPeer(links, koin.get<SessionStorage>(), koin.get(), { "isolated-watch" }, { true })
        val peer = assertNotNull(peers.current())
        val members = WindowMembershipStore(storage, RecordKind.MOMENT)
        val transactions = koin.get<SyncTransactionManager>(WatchCloud)
        val returned = MomentWindowApplier(peers, moments, outbox, members, transactions, accepts)
        val applier =
            WindowApplier(
                peers,
                koin.get<JournalNotesRepository>() as SyncableJournalNotesRepository,
                outbox,
                WindowMembershipStore(storage),
                transactions,
                {},
                momentWindow = returned,
            )
        val remote =
            assertNotNull(repository.getMark(mark.id)).copy(
                state = MarkState.FILLED,
                caption = "Phone resolution",
                resolvedNoteIds = setOf(Uuid.random()),
                lastUpdated = at + 1.milliseconds,
                syncVersion = 7,
            )
        val envelope = MomentEnvelopeBody.envelope(remote, RecordOrigin.PHONE).copy(context = peer)
        val item =
            WindowItem(
                peer.phoneNode,
                WearPaths.windowItem(RecordKind.MOMENT, mark.id, peer),
                EnvelopeDataMap.toDataMap(envelope, 1) + (WindowTombstoneDataMap.KEY_REVISION to "1"),
            )
        val response =
            WindowResyncResponse(
                context = peer,
                members =
                    listOf(
                        WindowMemberRef(
                            RecordKind.MOMENT,
                            mark.id.toString(),
                            remote.lastUpdated.toEpochMilliseconds(),
                            1,
                        ),
                    ),
                publishedAt = 9,
                revision = 1,
            )
        return Fixture(mark, peer, members, applier, item, response)
    }

    private suspend fun pending(id: Uuid) = metadata.getPendingUploads(EntityType.MOMENT).single { it.entityId == id.toString() }

    private data class Fixture(
        val mark: MarkedMomentEntity,
        val peer: PeerDeliveryContext,
        val members: WindowMembershipStore,
        val applier: WindowApplier,
        val item: WindowItem,
        val response: WindowResyncResponse,
    )
}
