package app.logdate.wear.sync.v2.delivery

import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsRequest
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.link.LinkWire
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WatchRemovalDeliveryTest {
    private val peer =
        PeerDeliveryContext(
            LinkWire.encodeKey(ByteArray(32) { 1 }),
            "phone",
            "watch",
            LinkMode.CLOUD,
            "account",
            "https://cloud.test",
        )
    private val pending =
        PendingUpload(
            Uuid.random().toString(),
            PendingOperation.DELETE,
            expectedServerVersion = 8,
            scope = UploadScope("account", "https://cloud.test"),
            operationId = "remove-operation",
        )
    private var queue = listOf(pending)
    private val outbox = mockk<SyncMetadataService>(relaxed = true)
    private val notes = mockk<JournalNotesRepository>()
    private val peers = mockk<WatchDeliveryPeer>()
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val receipts = WatchReceiptApplier(ledger, peers)
    private val released = ReleasedToCloud(ledger, peers, outbox)
    private var route = RouteConditions(PhoneReach.NEARBY, CloudPresence.SIGNED_IN, true, true, WatchNetwork.UNMETERED, false)
    private var answer: Receipt? = null
    private val puts = mutableListOf<Map<String, String>>()
    private var query: ReceiptsRequest? = null
    private var cleanup = 0
    private var cloudEligible = false
    private var cloudCompletes = false
    private val transport =
        object : DataLayerTransportV2 {
            override suspend fun reach() = route.phone

            override suspend fun put(
                path: String,
                data: Map<String, String>,
            ): Boolean {
                puts += data
                return true
            }

            override suspend fun deleteOwn(path: String): Boolean {
                cleanup++
                return true
            }

            override suspend fun sendFile(
                channelPath: String,
                localFilePath: String,
            ) = error("A removal has no recording")

            override suspend fun request(
                path: String,
                payload: ByteArray,
            ): Result<ByteArray> {
                query = (WearJson.decode(payload, 1, ReceiptsRequest.serializer()) as Decoded.Supported).value
                return Result.success(
                    WearJson.encodeToBytes(
                        ReceiptsResponse.serializer(),
                        ReceiptsResponse(cloud = CloudPresence.SIGNED_IN, receipts = listOfNotNull(answer), context = peer),
                    ),
                )
            }
        }
    private val delivery =
        WatchNoteDelivery(
            { true },
            peers,
            outbox,
            notes,
            ledger,
            { transport },
            { _, _ -> route },
            receipts,
            released,
            CloudRelease {
                cloudEligible = released.isEligible(EntityType.NOTE, pending)
                if (cloudCompletes) queue = emptyList()
                SyncResult(success = true)
            },
        )

    @Test
    fun `direct cloud completion removes the operation data item`() =
        runTest {
            route = route.copy(phone = PhoneReach.UNREACHABLE)
            cloudCompletes = true
            delivery.deliverIfActive()
            assertTrue(cloudEligible)
            assertEquals(1, cleanup)
        }

    init {
        coEvery { peers.current() } returns peer
        coEvery { peers.isCurrent(peer) } returns true
        coEvery { notes.getNoteById(any()) } returns null
        coEvery { outbox.getPendingUploads(EntityType.NOTE) } answers { queue }
        coEvery { outbox.isCurrentOperation(EntityType.NOTE, pending) } answers { pending in queue }
        coEvery { outbox.settleIfCurrent(EntityType.NOTE, pending, any(), any()) } answers {
            val current = pending in queue
            if (current) queue = emptyList()
            current
        }
    }

    @Test
    fun `a queued removal is delivered without loading its deleted note`() =
        runTest {
            delivery.deliverIfActive()
            val envelope = (EnvelopeDataMap.fromDataMap(puts.single()) as Decoded.Supported).value
            assertTrue(envelope.removed)
            assertEquals(peer, envelope.context)
            assertEquals(pending.operationId, envelope.operationId)
            assertEquals(8L, envelope.syncVersion)
            assertTrue(assertNotNull(query).items.single().removed)
            assertEquals(listOf(pending), queue)
        }

    @Test
    fun `a capture receipt cannot settle a queued removal`() =
        runTest {
            delivery.deliverIfActive()
            answer = Receipt(RecordKind.NOTE, pending.entityId, 0, ReceiptState.BACKED_UP, 8, pending.operationId)
            delivery.deliverIfActive()
            assertEquals(listOf(pending), queue)
            assertEquals(0, cleanup)
        }

    @Test
    fun `a confirmed removal settles only its current operation and cleans up its item`() =
        runTest {
            delivery.deliverIfActive()
            answer =
                Receipt(
                    RecordKind.NOTE,
                    pending.entityId,
                    0,
                    ReceiptState.DELETED_ON_PHONE,
                    operationId = pending.operationId,
                    removed = true,
                )
            val result = assertNotNull(delivery.deliverIfActive())
            assertTrue(queue.isEmpty())
            assertEquals(1, result.uploadedItems)
            assertEquals(1, cleanup)
        }

    @Test
    fun `a phone away removal is released only to its scoped cloud operation`() =
        runTest {
            route = route.copy(phone = PhoneReach.UNREACHABLE)
            delivery.deliverIfActive()
            assertTrue(cloudEligible)
            assertFalse(released.isEligible(EntityType.NOTE, pending))
        }
}
