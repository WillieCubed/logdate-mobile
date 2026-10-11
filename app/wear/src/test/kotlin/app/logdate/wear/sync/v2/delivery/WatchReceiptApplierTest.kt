package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** Tests [WatchReceiptApplier]: the phone's receipts move the watch's ledger forward and nothing else. */
class WatchReceiptApplierTest {
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val applier = WatchReceiptApplier(ledger)
    private val id = Uuid.random().toString()

    @Test
    fun `a receipt moves its note forward and is counted`() =
        runTest {
            track(version = 5)

            assertEquals(1, applier.apply(bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP, serverVersion = 7))))
            assertEquals(DeliveryState.BACKED_UP, state())
            assertEquals(CloudPresence.SIGNED_IN, applier.phoneCloud())
        }

    @Test
    fun `a receipt for an older version moves nothing`() =
        runTest {
            track(version = 6)

            assertEquals(0, applier.apply(bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP))))
            assertEquals(DeliveryState.NEW, state())
        }

    @Test
    fun `a rejection covers every version`() =
        runTest {
            track(version = 5)

            applier.apply(bytes(Receipt(RecordKind.NOTE, id, Long.MAX_VALUE, ReceiptState.REJECTED_UNSUPPORTED)))

            assertEquals(DeliveryState.REJECTED, state())
        }

    @Test
    fun `receipts for other kinds are ignored`() =
        runTest {
            track(version = 5)

            assertEquals(0, applier.apply(bytes(Receipt(RecordKind.MOMENT, id, 5, ReceiptState.BACKED_UP))))
        }

    @Test
    fun `receipts this build cannot read change nothing`() =
        runTest {
            track(version = 5)

            assertEquals(0, applier.apply("nope".encodeToByteArray()))
            assertNull(applier.phoneCloud())
        }

    @Test
    fun `a backup receipt without a positive server version is not proof`() =
        runTest {
            track(version = 5)
            for (version in listOf(null, 0L, -1L)) {
                assertEquals(0, applier.apply(bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.BACKED_UP, serverVersion = version))))
                assertEquals(DeliveryState.NEW, state())
            }
        }

    @Test
    fun `a repeated rejection moves nothing and does not start another sync`() =
        runTest {
            track(version = 5)
            val rejected = bytes(Receipt(RecordKind.NOTE, id, Long.MAX_VALUE, ReceiptState.REJECTED_UNSUPPORTED))
            assertEquals(1, applier.apply(rejected))
            assertEquals(0, applier.apply(rejected))
            assertEquals(DeliveryState.REJECTED, state())
        }

    @Test
    fun `repeated on phone receipts retain the original grace period`() =
        runTest {
            track(version = 5)
            val received = bytes(Receipt(RecordKind.NOTE, id, 5, ReceiptState.ON_PHONE))
            assertEquals(1, applier.apply(received))
            val original = ledger.get(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id)
            assertEquals(0, applier.apply(received))
            assertEquals(original, ledger.get(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id))
        }

    private suspend fun track(version: Long) = ledger.track(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id, version)

    private suspend fun state() = ledger.get(PeerScopes.PHONE, DeliveryDirection.TO_PHONE, EntityType.NOTE, id)?.state

    private fun bytes(receipt: Receipt) =
        WearJson.encodeToBytes(ReceiptsResponse.serializer(), ReceiptsResponse(cloud = CloudPresence.SIGNED_IN, receipts = listOf(receipt)))
}
