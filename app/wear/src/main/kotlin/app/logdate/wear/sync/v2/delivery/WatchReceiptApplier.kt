package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import io.github.aakira.napier.Napier

/** Applies the phone's receipts, pushed or answered, to the watch's ledger. A receipt only ever moves a note forward. */
class WatchReceiptApplier(
    private val ledger: PeerDeliveryLedger,
) {
    @Volatile
    private var lastCloud: CloudPresence? = null

    /** What the phone last said about its account in a receipt; null before the first. */
    fun phoneCloud(): CloudPresence? = lastCloud

    /** Returns how many notes moved forward; receipts this build cannot read move none and are asked for again. */
    suspend fun apply(payload: ByteArray): Int {
        val response =
            when (val decoded = WearJson.decode(payload, WearProtocol.RECEIPTS_SCHEMA, ReceiptsResponse.serializer())) {
                is Decoded.Supported -> decoded.value
                is Decoded.Unsupported -> {
                    Napier.w("The phone's receipts use schema ${decoded.schema}; the watch waits and asks again")
                    return 0
                }
                is Decoded.Malformed -> {
                    Napier.e("The phone's receipts could not be read: ${decoded.reason}; the watch asks again")
                    return 0
                }
            }
        lastCloud = response.cloud
        return response.receipts.count { applyOne(it) }
    }

    private suspend fun applyOne(receipt: Receipt): Boolean {
        if (receipt.kind != RecordKind.NOTE) {
            Napier.w("Ignoring a ${receipt.kind} receipt; this watch sends only notes this way")
            return false
        }
        if (receipt.state == ReceiptState.BACKED_UP && (receipt.serverVersion ?: 0L) <= 0L) {
            Napier.w("Ignoring a backup receipt with no positive server version")
            return false
        }
        val moved =
            ledger.applyReceipt(
                scope = PeerScopes.PHONE,
                direction = DeliveryDirection.TO_PHONE,
                entityType = EntityType.NOTE,
                entityId = receipt.id,
                receiptVersion = receipt.contentVersion,
                state = DeliveryState.of(receipt.state),
            )
        return moved != null
    }
}
