package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsRequest
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.MomentEnvelopeBody
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.wear.sync.v2.delivery.CloudRelease
import app.logdate.wear.sync.v2.delivery.DataLayerTransportV2
import app.logdate.wear.sync.v2.delivery.ReleasedToCloud
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.delivery.WatchMomentDelivery
import app.logdate.wear.sync.v2.delivery.WatchReceiptApplier
import app.logdate.wear.sync.v2.link.WatchLinkStore
import app.logdate.wear.v2.mark.MarkedMomentStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Real watch persistence with a controlled remote receipt boundary; no paired-device claim. */
@RunWith(AndroidJUnit4::class)
class WatchMomentDeliveryRoomTest {
    private val koin get() = KoinJavaComponent.getKoin()

    @Test fun exactPhoneReceiptSettlesTheRoomOperationAndRetainsTheMark() =
        runBlocking {
            val f = fixture()
            f.delivery.deliverIfActive()
            val sent = f.transport.puts.single()
            val envelope = (EnvelopeDataMap.fromDataMap(sent.second) as Decoded.Supported).value
            assertEquals(f.peer, envelope.context)
            assertEquals(f.pending().operationId, envelope.operationId)
            assertEquals(f.mark.id, MomentEnvelopeBody.decode(envelope)?.id)
            assertTrue(f.metadata.hasPending(EntityType.MOMENT, f.mark.id.toString()))
            f.transport.prove = true
            assertEquals(1, f.delivery.deliverIfActive()?.uploadedItems)
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
            assertNotNull(koin.get<MarkedMomentDao>().getById(f.mark.id))
            assertEquals(listOf(sent.first), f.transport.deleted)
        }

    @Test fun olderPhoneReceiptCannotClearANewerRoomSensorOperation() =
        runBlocking {
            val f = fixture()
            f.delivery.deliverIfActive()
            val original = f.pending()
            f.transport.prove = true
            f.transport.beforeAnswer = { koin.get<MarkedMomentStore>().update(f.mark.copy(heartRateBpm = 72)) }
            f.delivery.deliverIfActive()
            assertNotEquals(original.operationId, f.pending().operationId)
            assertEquals(72, koin.get<MarkedMomentDao>().getById(f.mark.id)?.heartRateBpm)
            assertEquals(listOf(WearPaths.watchRecord(RecordKind.MOMENT, f.mark.id, f.peer, original.operationId!!)), f.transport.deleted)
            assertTrue(WearPaths.watchRecord(RecordKind.MOMENT, f.mark.id, f.peer, f.pending().operationId!!) !in f.transport.deleted)
        }

    private suspend fun fixture(): Fixture {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val mark =
            MarkedMomentEntity(
                id = Uuid.random(),
                capturedAt = now,
                timeZoneId = "UTC",
                state = MarkedMomentState.OPEN,
                originDeviceId = "watch",
                lastUpdated = now,
            )
        koin.get<MarkedMomentStore>().create(mark)
        val metadata = koin.get<SyncMetadataService>()
        val isolated =
            object : SyncMetadataService by metadata {
                override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> =
                    metadata.getPendingUploads(entityType).filter { it.entityId == mark.id.toString() }
            }
        var link: String? = null
        val storage =
            object : KeyValueStorage by koin.get<KeyValueStorage>() {
                override suspend fun getString(key: String) = link

                override suspend fun putString(
                    key: String,
                    value: String,
                ) {
                    link = value
                }
            }
        val vault =
            object : PairKeyVault by koin.get<PairKeyVault>() {
                override suspend fun put(
                    peer: String,
                    key: ByteArray,
                ) = Unit
            }
        val links = WatchLinkStore(storage, vault)
        links.savePairing("phone", ByteArray(32) { 1 }, LinkWire.encodeKey(ByteArray(32) { 2 }))
        val sessions =
            object : SessionStorage by koin.get<SessionStorage>() {
                override fun getSession() = null

                override fun getOriginBoundSession() = null
            }
        val peers = WatchDeliveryPeer(links, sessions, koin.get(), { "watch" }, { true })
        val peer = assertNotNull(peers.current())
        val ledger = PeerDeliveryLedger(koin.get())
        val transport = Transport(peer)
        val delivery =
            WatchMomentDelivery(
                { true },
                peers,
                isolated,
                koin.get(),
                ledger,
                { transport },
                { _, _ -> RouteConditions(PhoneReach.NEARBY, CloudPresence.NO_ACCOUNT, false, false, WatchNetwork.NONE, false) },
                WatchReceiptApplier(ledger, peers),
                ReleasedToCloud(ledger, peers, isolated),
                CloudRelease { error("Pair-only marks never use direct cloud") },
                { true },
            )
        return Fixture(mark, peer, isolated, transport, delivery)
    }

    private data class Fixture(
        val mark: MarkedMomentEntity,
        val peer: PeerDeliveryContext,
        val metadata: SyncMetadataService,
        val transport: Transport,
        val delivery: WatchMomentDelivery,
    ) {
        suspend fun pending() = metadata.getPendingUploads(EntityType.MOMENT).single()
    }

    private class Transport(
        private val peer: PeerDeliveryContext,
    ) : DataLayerTransportV2 {
        val puts = mutableListOf<Pair<String, Map<String, String>>>()
        val deleted = mutableListOf<String>()
        var prove = false
        var beforeAnswer: suspend () -> Unit = {}

        override suspend fun reach() = PhoneReach.NEARBY

        override suspend fun put(
            path: String,
            data: Map<String, String>,
        ): Boolean {
            puts += path to data
            return true
        }

        override suspend fun deleteOwn(path: String): Boolean {
            deleted += path
            return true
        }

        override suspend fun sendFile(
            channelPath: String,
            localFilePath: String,
        ) = error("Marks have no media")

        override suspend fun request(
            path: String,
            payload: ByteArray,
        ): Result<ByteArray> {
            assertEquals(WearPaths.RECEIPTS, path)
            val query = (WearJson.decode(payload, WearProtocol.RECEIPTS_SCHEMA, ReceiptsRequest.serializer()) as Decoded.Supported).value
            beforeAnswer()
            val receipts =
                if (prove) {
                    query.items.map {
                        Receipt(it.kind, it.id, it.contentVersion, ReceiptState.ON_PHONE, operationId = it.operationId)
                    }
                } else {
                    emptyList()
                }
            return Result.success(
                WearJson.encodeToBytes(
                    ReceiptsResponse.serializer(),
                    ReceiptsResponse(cloud = CloudPresence.NO_ACCOUNT, context = peer, receipts = receipts),
                ),
            )
        }
    }
}
