package app.logdate.wear.sync.v2.delivery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.datalayer.AudioFileDigest
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
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.HoldReason
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.sync.AudioFileDigester
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class WatchNoteDeliveryTest {
    private var now = 1_700_000_000_000L
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(now)
        }
    private var peer =
        PeerDeliveryContext(
            LinkWire.encodeKey(ByteArray(32) { 1 }),
            "phone",
            "watch",
            LinkMode.CLOUD,
            "account",
            "https://cloud.test",
        )
    private var currentPeer: PeerDeliveryContext? = peer
    private var enabled = true
    private val note =
        JournalNote.Text(
            content = "Watch capture",
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
        )
    private var stored: JournalNote? = note
    private val original =
        PendingUpload(
            note.uid.toString(),
            PendingOperation.CREATE,
            scope = UploadScope("account", "https://cloud.test"),
            operationId = "operation",
        )
    private var queue = listOf(original)
    private val outbox = mockk<SyncMetadataService>(relaxed = true)
    private val notes = mockk<JournalNotesRepository>()
    private val peers = mockk<WatchDeliveryPeer>()
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao(), clock)
    private val receipts = WatchReceiptApplier(ledger, peers)
    private val released = ReleasedToCloud(ledger, peers, outbox)
    private var route = RouteConditions(PhoneReach.NEARBY, CloudPresence.SIGNED_IN, true, true, WatchNetwork.UNMETERED, false)
    private var response: ReceiptsResponse? = null
    private var cloudResult = SyncResult(success = true)
    private var cloudCalls = 0
    private var cloudWasEligible = false
    private val transport = Transport()
    private var digest: suspend (String) -> AudioFileDigest? = { AudioFileDigest(42, "ab".repeat(32)) }
    private val delivery =
        WatchNoteDelivery(
            { enabled },
            peers,
            outbox,
            notes,
            ledger,
            { transport },
            { _, _ -> route },
            receipts,
            released,
            CloudRelease {
                cloudCalls++
                cloudWasEligible = released.isEligible(EntityType.NOTE, original)
                cloudResult
            },
            AudioFileDigester { digest(it) },
            clock,
        )

    init {
        coEvery { peers.current() } answers { currentPeer }
        coEvery { peers.isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == currentPeer }
        coEvery { notes.getNoteById(any()) } answers { stored }
        coEvery { outbox.getPendingUploads(EntityType.NOTE) } answers { queue }
        coEvery { outbox.hasPending(EntityType.NOTE, any()) } answers { queue.any { it.entityId == secondArg<String>() } }
        coEvery { outbox.isCurrentOperation(EntityType.NOTE, any()) } answers {
            queue.any { it.operationId == secondArg<PendingUpload>().operationId }
        }
        coEvery { outbox.settleIfCurrent(EntityType.NOTE, any(), any(), any()) } answers {
            val captured = secondArg<PendingUpload>()
            val found = queue.any { it.operationId == captured.operationId }
            if (found) queue = queue.filterNot { it.operationId == captured.operationId }
            found
        }
    }

    @Test
    fun `inactive delivery leaves notes to the existing manager`() =
        runTest {
            enabled = false
            assertNull(delivery.deliverIfActive())
            assertTrue(transport.puts.isEmpty())
            assertEquals(listOf(original), queue)
        }

    @Test
    fun `a new capture is scoped to the verified exchange and waits for a receipt`() =
        runTest {
            assertTrue(assertNotNull(delivery.deliverIfActive()).success)
            val envelope = assertNotNull((EnvelopeDataMap.fromDataMap(transport.puts.single().second) as? Decoded.Supported)?.value)
            assertEquals(peer, envelope.context)
            assertEquals("operation", envelope.operationId)
            assertEquals(
                "operation",
                transport.requests
                    .single()
                    .items
                    .single()
                    .operationId,
            )
            assertEquals(note.uid.toString(), envelope.id)
            assertEquals(DeliveryState.SENT, row()?.state)
            assertEquals(now + 30_000, row()?.nextAttemptAt)
            assertEquals(listOf(original), queue)
            assertEquals(peer, transport.requests.single().context)
        }

    @Test
    fun `a recording travels on its link-scoped channel`() =
        runTest {
            stored =
                JournalNote.Audio(
                    uid = note.uid,
                    mediaRef = "/recording.m4a",
                    creationTimestamp = note.creationTimestamp,
                    lastUpdated = note.lastUpdated,
                )
            delivery.deliverIfActive()
            assertEquals(listOf(WearPaths.recordMedia(RecordKind.NOTE, note.uid, peer, "operation") to "/recording.m4a"), transport.files)
            val envelope = (EnvelopeDataMap.fromDataMap(transport.puts.single().second) as Decoded.Supported).value
            assertEquals(42L, envelope.media.single().sizeBytes)
        }

    @Test
    fun `a failed send waits before another attempt`() =
        runTest {
            transport.putSucceeds = false
            assertFalse(assertNotNull(delivery.deliverIfActive()).success)
            assertEquals(HoldReason.SEND_FAILED, row()?.holdReason)
            assertEquals(now + 30_000, row()?.nextAttemptAt)
            delivery.deliverIfActive()
            assertEquals(1, transport.puts.size)
            now += 30_000
            delivery.deliverIfActive()
            assertEquals(2, transport.puts.size)
            assertEquals(now + 60_000, row()?.nextAttemptAt)
        }

    @Test
    fun `a matching backup receipt settles only the captured operation`() =
        runTest {
            response = proof(ReceiptState.BACKED_UP)
            assertEquals(1, assertNotNull(delivery.deliverIfActive()).uploadedItems)
            assertTrue(queue.isEmpty())
            assertTrue(transport.puts.isEmpty())
            assertEquals(listOf(WearPaths.watchRecord(RecordKind.NOTE, note.uid, peer, "operation")), transport.deleted)
        }

    @Test
    fun `a fresh pair-only storage receipt can settle local delivery`() =
        runTest {
            pairOnly()
            response = proof(ReceiptState.ON_PHONE, CloudPresence.NO_ACCOUNT)
            delivery.deliverIfActive()
            assertTrue(queue.isEmpty())
        }

    @Test
    fun `a cached no-account hint cannot settle a capture without fresh proof`() =
        runTest {
            pairOnly()
            val tracked = ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, original.entityId, 5)
            ledger.moveTo(tracked, DeliveryState.ON_PHONE)
            receipts.apply(
                "phone",
                WearJson.encodeToBytes(ReceiptsResponse.serializer(), proof(ReceiptState.ON_PHONE, CloudPresence.NO_ACCOUNT)),
            )
            route = route.copy(phoneCloud = CloudPresence.NO_ACCOUNT)
            delivery.deliverIfActive()
            assertEquals(listOf(original), queue)
        }

    @Test
    fun `a newer edit queued during the request stays pending`() =
        runTest {
            response = proof(ReceiptState.BACKED_UP)
            transport.beforeAnswer = { queue = listOf(original.copy(operationId = "new", operation = PendingOperation.UPDATE)) }
            delivery.deliverIfActive()
            assertEquals("new", queue.single().operationId)
            assertEquals(listOf(WearPaths.watchRecord(RecordKind.NOTE, note.uid, peer, "operation")), transport.deleted)
        }

    @Test
    fun `a linked capture can be released to cloud with its exact operation`() =
        runTest {
            route = route.copy(phone = PhoneReach.UNREACHABLE)
            delivery.deliverIfActive()
            assertEquals(1, cloudCalls)
            assertTrue(cloudWasEligible)
            assertFalse(released.isEligible(EntityType.NOTE, original))
            assertEquals(DeliveryState.CLOUD_PENDING, row()?.state)
        }

    @Test
    fun `a cloud failure without errors still fails the delivery run`() =
        runTest {
            route = route.copy(phone = PhoneReach.UNREACHABLE)
            cloudResult = SyncResult(success = false)
            val result = assertNotNull(delivery.deliverIfActive())
            assertFalse(result.success)
            assertTrue(result.errors.isNotEmpty())
            assertEquals(listOf(original), queue)
        }

    @Test
    fun `an unavailable verified peer never falls back to an arbitrary phone`() =
        runTest {
            currentPeer = null
            assertNotNull(delivery.deliverIfActive())
            assertTrue(transport.puts.isEmpty())
            assertEquals(0, cloudCalls)
            assertEquals(listOf(original), queue)
        }

    @Test
    fun `a link change during receipt lookup stops the old run`() =
        runTest {
            transport.beforeAnswer = { currentPeer = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 })) }
            delivery.deliverIfActive()
            assertTrue(transport.puts.isEmpty())
            assertEquals(0, cloudCalls)
            assertEquals(listOf(original), queue)
        }

    @Test
    fun `recording cancellation propagates without a send or release`() =
        runTest {
            stored =
                JournalNote.Audio(
                    uid = note.uid,
                    mediaRef = "/recording.m4a",
                    creationTimestamp = note.creationTimestamp,
                    lastUpdated = note.lastUpdated,
                )
            digest = { throw CancellationException("stopped") }
            assertFailsWith<CancellationException> { delivery.deliverIfActive() }
            assertTrue(transport.puts.isEmpty())
            assertEquals(0, cloudCalls)
        }

    @Test
    fun `a failed recording transfer keeps the capture queued with a retry`() =
        runTest {
            stored =
                JournalNote.Audio(
                    uid = note.uid,
                    mediaRef = "/recording.m4a",
                    creationTimestamp = note.creationTimestamp,
                    lastUpdated = note.lastUpdated,
                )
            transport.fileSucceeds = false
            assertFalse(assertNotNull(delivery.deliverIfActive()).success)
            assertEquals(HoldReason.SEND_FAILED, row()?.holdReason)
            assertEquals(listOf(original), queue)
        }

    @Test
    fun `an unreadable recording is never sent without its integrity metadata`() =
        runTest {
            stored =
                JournalNote.Audio(
                    uid = note.uid,
                    mediaRef = "/recording.m4a",
                    creationTimestamp = note.creationTimestamp,
                    lastUpdated = note.lastUpdated,
                )
            digest = { null }
            assertFalse(assertNotNull(delivery.deliverIfActive()).success)
            assertTrue(transport.puts.isEmpty())
            assertEquals(listOf(original), queue)
        }

    @Test
    fun `an edit queued during hashing prevents the captured recording from being sent`() =
        runTest {
            stored =
                JournalNote.Audio(
                    uid = note.uid,
                    mediaRef = "/recording.m4a",
                    creationTimestamp = note.creationTimestamp,
                    lastUpdated = note.lastUpdated,
                )
            digest = {
                queue = listOf(original.copy(operationId = "edit", operation = PendingOperation.UPDATE))
                AudioFileDigest(42, "ab".repeat(32))
            }
            delivery.deliverIfActive()
            assertEquals("edit", queue.single().operationId)
            assertTrue(transport.puts.isEmpty())
            assertTrue(transport.files.isEmpty())
        }

    @Test
    fun `the phone gets its backup grace period before direct fallback`() =
        runTest {
            response = proof(ReceiptState.ON_PHONE)
            delivery.deliverIfActive()
            assertEquals(0, cloudCalls)
            assertEquals(HoldReason.PHONE_BACKING_UP, row()?.holdReason)
            assertEquals(now + 15 * 60_000, row()?.nextAttemptAt)
            now += 15 * 60_000
            delivery.deliverIfActive()
            assertEquals(1, cloudCalls)
            assertTrue(cloudWasEligible)
        }

    @Test
    fun `an edit waits for the phone even when the watch has independent internet`() =
        runTest {
            queue = listOf(original.copy(operation = PendingOperation.UPDATE))
            route = route.copy(phone = PhoneReach.UNREACHABLE)
            delivery.deliverIfActive()
            assertEquals(0, cloudCalls)
            assertEquals(HoldReason.EDIT_NEEDS_PHONE, row()?.holdReason)
            assertFalse(queue.isEmpty())
        }

    @Test
    fun `a receipt from a different exchange cannot settle the capture`() =
        runTest {
            response = proof(ReceiptState.BACKED_UP).copy(context = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 })))
            delivery.deliverIfActive()
            assertEquals(listOf(original), queue)
            assertEquals(DeliveryState.SENT, row()?.state)
            assertTrue(transport.deleted.isEmpty())
        }

    @Test
    fun `an earlier receipt cannot settle a new operation with the same timestamp`() =
        runTest {
            queue = listOf(original.copy(operationId = "new", operation = PendingOperation.UPDATE))
            response = proof(ReceiptState.BACKED_UP)
            delivery.deliverIfActive()
            assertEquals(listOf("new"), queue.map { it.operationId })
            assertEquals(1, transport.puts.size)
            assertTrue(transport.deleted.isEmpty())
        }

    @Test
    fun `terminal history from an earlier operation cannot settle a restarted edit`() =
        runTest {
            val previous = ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, original.entityId + "@operation", 5)
            ledger.moveTo(previous, DeliveryState.BACKED_UP)
            queue = listOf(original.copy(operationId = "new", operation = PendingOperation.UPDATE))
            route = route.copy(phone = PhoneReach.UNREACHABLE)
            delivery.deliverIfActive()
            assertEquals(listOf("new"), queue.map { it.operationId })
            assertEquals(listOf(WearPaths.watchRecord(RecordKind.NOTE, note.uid, peer, "operation")), transport.deleted)
            assertEquals(0, cloudCalls)
        }

    @Test
    fun `an operationless receipt cannot impersonate a scoped ledger key`() =
        runTest {
            for (id in listOf(original.entityId, original.entityId + "@operation")) {
                response =
                    proof(ReceiptState.BACKED_UP).let {
                        it.copy(receipts = it.receipts.map { receipt -> receipt.copy(id = id, operationId = null) })
                    }
                delivery.deliverIfActive()
                assertEquals(listOf(original), queue)
                assertTrue(transport.deleted.isEmpty())
            }
        }

    private fun pairOnly() {
        peer = peer.copy(mode = LinkMode.PAIR, accountId = null, serverOrigin = null)
        currentPeer = peer
        route = route.copy(linked = false, serverReady = false)
    }

    private fun proof(
        state: ReceiptState,
        cloud: CloudPresence = CloudPresence.SIGNED_IN,
    ): ReceiptsResponse {
        val serverVersion = if (state == ReceiptState.BACKED_UP) 7L else null
        val receipt = Receipt(RecordKind.NOTE, original.entityId, 5, state, serverVersion, operationId = "operation")
        return ReceiptsResponse(cloud = cloud, context = peer, receipts = listOf(receipt))
    }

    private suspend fun row() = ledger.get(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, original.entityId + "@operation")

    private inner class Transport : DataLayerTransportV2 {
        val puts = mutableListOf<Pair<String, Map<String, String>>>()
        val files = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()
        val requests = mutableListOf<ReceiptsRequest>()
        var putSucceeds = true
        var fileSucceeds = true
        var beforeAnswer: suspend () -> Unit = {}

        override suspend fun reach() = route.phone

        override suspend fun put(
            path: String,
            data: Map<String, String>,
        ): Boolean {
            puts += path to data
            return putSucceeds
        }

        override suspend fun deleteOwn(path: String): Boolean {
            deleted += path
            return true
        }

        override suspend fun sendFile(
            channelPath: String,
            localFilePath: String,
        ): Boolean {
            files += channelPath to localFilePath
            return fileSucceeds
        }

        override suspend fun request(
            path: String,
            payload: ByteArray,
        ): Result<ByteArray> {
            assertEquals(WearPaths.RECEIPTS, path)
            requests += (WearJson.decode(payload, WearProtocol.RECEIPTS_SCHEMA, ReceiptsRequest.serializer()) as Decoded.Supported).value
            beforeAnswer()
            return Result.success(
                WearJson.encodeToBytes(
                    ReceiptsResponse.serializer(),
                    response ?: ReceiptsResponse(
                        cloud = if (peer.mode == LinkMode.CLOUD) CloudPresence.SIGNED_IN else CloudPresence.NO_ACCOUNT,
                        context = peer,
                        receipts = emptyList(),
                    ),
                ),
            )
        }
    }
}
