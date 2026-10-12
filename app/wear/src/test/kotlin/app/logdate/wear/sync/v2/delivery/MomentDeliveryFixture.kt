package app.logdate.wear.sync.v2.delivery

import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
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
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.peer.delivery.CaptureIdentity
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.link.LinkWire
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MomentDeliveryFixture {
    var now = 1000L
    val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(now)
        }
    var peer =
        PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.CLOUD, "account", "https://cloud.test")
    var current: PeerDeliveryContext? = peer
    var active = true
    var supported = true
    val moment =
        MarkedMoment(
            Uuid.random(),
            Instant.fromEpochMilliseconds(1),
            "UTC",
            null,
            72,
            MarkState.OPEN,
            "Capture",
            emptySet(),
            "watch",
            Instant.fromEpochMilliseconds(5),
            0,
        )
    var stored: MarkedMoment? = moment
    val original =
        PendingUpload(
            moment.id.toString(),
            PendingOperation.CREATE,
            scope = UploadScope("account", "https://cloud.test"),
            operationId = "mark-operation",
        )
    var queue = listOf(original)
    val outbox = mockk<SyncMetadataService>(relaxed = true)
    val peers = mockk<WatchDeliveryPeer>()
    val moments = mockk<MarkedMomentRepository>()
    val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao(), clock)
    val receipts = WatchReceiptApplier(ledger, peers)
    val released = ReleasedToCloud(ledger, peers, outbox)
    var route = RouteConditions(PhoneReach.NEARBY, CloudPresence.SIGNED_IN, true, true, WatchNetwork.UNMETERED, false)
    var response: ReceiptsResponse? = null
    var cloudCalls = 0
    var cloudWasEligible = false
    var cloudResult = SyncResult(success = true)
    var cloudAction: suspend () -> Unit = {}
    val transport = Transport()
    val delivery =
        WatchMomentDelivery(
            { active },
            peers,
            outbox,
            moments,
            ledger,
            { transport },
            { _, _ -> route },
            receipts,
            released,
            CloudRelease {
                cloudCalls++
                cloudWasEligible = queue.firstOrNull()?.let { released.isEligible(EntityType.MOMENT, it) } == true
                cloudAction()
                cloudResult
            },
            { supported },
            clock,
        )

    init {
        coEvery { peers.current() } answers { current }
        coEvery { peers.isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == current }
        coEvery { moments.getMark(any()) } answers { stored?.takeIf { it.id == firstArg<Uuid>() } }
        coEvery { outbox.getPendingUploads(EntityType.MOMENT) } answers { queue }
        coEvery { outbox.isCurrentOperation(EntityType.MOMENT, any()) } answers { secondArg<PendingUpload>() in queue }
        coEvery { outbox.settleIfCurrent(EntityType.MOMENT, any(), any(), any()) } answers {
            val captured = secondArg<PendingUpload>()
            val found = captured in queue
            if (found) queue = queue - captured
            found
        }
    }

    suspend fun row(pending: PendingUpload = original) =
        ledger.get(
            peer.ledgerScope,
            DeliveryDirection.TO_PHONE,
            EntityType.MOMENT,
            CaptureIdentity.of(pending.entityId, pending.operationId)!!.let {
                if (pending.operation ==
                    PendingOperation.DELETE
                ) {
                    it.removalLedgerKey
                } else {
                    it.ledgerKey
                }
            },
        )

    fun proof(
        state: ReceiptState,
        removed: Boolean = false,
        operation: String = original.operationId!!,
    ): ReceiptsResponse =
        ReceiptsResponse(
            cloud = if (peer.mode == LinkMode.CLOUD) CloudPresence.SIGNED_IN else CloudPresence.NO_ACCOUNT,
            context = peer,
            receipts =
                listOf(
                    Receipt(
                        RecordKind.MOMENT,
                        original.entityId,
                        if (removed) 0 else 5,
                        state,
                        serverVersion = 7.takeIf { state == ReceiptState.BACKED_UP }?.toLong(),
                        operationId = operation,
                        removed = removed,
                    ),
                ),
        )

    fun pairOnly() {
        peer = peer.copy(mode = LinkMode.PAIR, accountId = null, serverOrigin = null)
        current = peer
        route =
            route.copy(linked = false, serverReady = false, phoneCloud = CloudPresence.NO_ACCOUNT)
    }

    inner class Transport : DataLayerTransportV2 {
        val puts = mutableListOf<Pair<String, Map<String, String>>>()
        val deleted = mutableListOf<String>()
        val requests = mutableListOf<ReceiptsRequest>()
        var succeeds = true
        var beforeAnswer: suspend () -> Unit = {}
        var beforePut: suspend () -> Unit = {}

        override suspend fun reach() = route.phone

        override suspend fun put(
            path: String,
            data: Map<String, String>,
        ): Boolean {
            puts += path to data
            beforePut()
            return succeeds
        }

        override suspend fun deleteOwn(path: String): Boolean {
            deleted += path
            return true
        }

        override suspend fun sendFile(
            channelPath: String,
            localFilePath: String,
        ): Boolean = error("Marks have no media channel")

        override suspend fun request(
            path: String,
            payload: ByteArray,
        ): Result<ByteArray> {
            requests += (WearJson.decode(payload, WearProtocol.RECEIPTS_SCHEMA, ReceiptsRequest.serializer()) as Decoded.Supported).value
            beforeAnswer()
            return Result.success(
                WearJson.encodeToBytes(
                    ReceiptsResponse.serializer(),
                    response ?: ReceiptsResponse(
                        cloud =
                            if (peer.mode ==
                                LinkMode.CLOUD
                            ) {
                                CloudPresence.SIGNED_IN
                            } else {
                                CloudPresence.NO_ACCOUNT
                            },
                        context = peer,
                        receipts = emptyList(),
                    ),
                ),
            )
        }
    }
}
