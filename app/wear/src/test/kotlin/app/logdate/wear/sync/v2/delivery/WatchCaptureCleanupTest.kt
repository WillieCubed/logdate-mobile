package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.peer.delivery.CaptureIdentity
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.link.LinkWire
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class WatchCaptureCleanupTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val id = Uuid.random()
    private val identity = CaptureIdentity.of(id.toString(), "old")!!
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val peers = mockk<WatchDeliveryPeer>()
    private val outbox = mockk<SyncMetadataService>()
    private val transport = mockk<DataLayerTransportV2>()
    private val cleanup = WatchCaptureCleanup(peers, outbox, ledger)
    private var queue = emptyList<PendingUpload>()

    init {
        coEvery { peers.isCurrent(peer) } returns true
        coEvery { outbox.getPendingUploads(EntityType.NOTE) } answers { queue }
        coEvery { transport.deleteOwn(any()) } returns true
    }

    private suspend fun track(key: String = identity.ledgerKey) =
        ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, key, 5)

    private suspend fun row(key: String = identity.ledgerKey) =
        ledger.get(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, key)

    private fun pending(
        operation: String = "old",
        kind: PendingOperation = PendingOperation.CREATE,
    ) = PendingUpload(id.toString(), kind, scope = UploadScope("local", "https://cloud.test"), operationId = operation)

    @Test
    fun `a superseded capture deletes only its operation and current link`() =
        runTest {
            track()
            queue = listOf(pending("new"))
            cleanup.prune(peer, transport)
            coVerify(exactly = 1) { transport.deleteOwn(WearPaths.watchRecord(RecordKind.NOTE, id, peer, "old")) }
            assertNull(row())
        }

    @Test
    fun `a still pending operation keeps its data item and ledger`() =
        runTest {
            track()
            queue = listOf(pending())
            cleanup.prune(peer, transport)
            coVerify(exactly = 0) { transport.deleteOwn(any()) }
            assertNotNull(row())
        }

    @Test
    fun `a completed removal also cleans its exact item`() =
        runTest {
            track(identity.removalLedgerKey)
            cleanup.prune(peer, transport)
            coVerify(exactly = 1) { transport.deleteOwn(WearPaths.watchRecord(RecordKind.NOTE, id, peer, "old")) }
            assertNull(row(identity.removalLedgerKey))
        }

    @Test
    fun `failed deletion retains history for a later cleanup attempt`() =
        runTest {
            track()
            coEvery { transport.deleteOwn(any()) } returns false
            cleanup.prune(peer, transport)
            assertNotNull(row())
        }

    @Test
    fun `a relink during deletion cannot forget the earlier exchanges history`() =
        runTest {
            track()
            coEvery { transport.deleteOwn(any()) } coAnswers {
                coEvery { peers.isCurrent(peer) } returns false
                true
            }
            cleanup.prune(peer, transport)
            assertNotNull(row())
        }
}
