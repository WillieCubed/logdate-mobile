package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.ReceiptsRequest
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import io.github.aakira.napier.Napier

/** Answers a watch's receipts request with what this phone has of each record it asks about. */
class WatchReceiptResponder(
    private val book: PhoneReceiptBook,
) {
    suspend fun answer(payload: ByteArray): ByteArray {
        val receipts =
            when (val decoded = WearJson.decode(payload, WearProtocol.RECEIPTS_SCHEMA, ReceiptsRequest.serializer())) {
                is Decoded.Supported -> decoded.value.items.mapNotNull { book.receiptFor(it) }
                is Decoded.Unsupported -> {
                    Napier.w("A watch asked for receipts with schema ${decoded.schema}; it gets none until this phone updates")
                    emptyList()
                }
                is Decoded.Malformed -> {
                    Napier.e("A watch's receipts request could not be read: ${decoded.reason}")
                    emptyList()
                }
            }
        return WearJson.encodeToBytes(ReceiptsResponse.serializer(), ReceiptsResponse(cloud = book.cloudPresence(), receipts = receipts))
    }
}
