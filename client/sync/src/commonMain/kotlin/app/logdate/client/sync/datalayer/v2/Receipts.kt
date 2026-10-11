package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable

/** What the phone has done with a record the watch sent. */
@Serializable
enum class ReceiptState {
    ON_PHONE,
    BACKED_UP,
    DELETED_ON_PHONE,

    /** The phone cannot read this kind or schema; the watch still has the record and shows why. */
    REJECTED_UNSUPPORTED,
}

/** Whether the phone backs up to the cloud, so the watch knows whether ON_PHONE is final. */
@Serializable
enum class CloudPresence {
    NO_ACCOUNT,
    SIGNED_IN,
    UNKNOWN,
}

@Serializable
data class ReceiptQuery(
    val kind: RecordKind,
    val id: String,
    val contentVersion: Long,
)

/** Sent with `MessageClient.sendRequest` to [WearPaths.RECEIPTS]. */
@Serializable
data class ReceiptsRequest(
    val schema: Int = WearProtocol.RECEIPTS_SCHEMA,
    val items: List<ReceiptQuery>,
    val context: PeerDeliveryContext? = null,
)

@Serializable
data class Receipt(
    val kind: RecordKind,
    val id: String,
    val contentVersion: Long,
    val state: ReceiptState,
    val serverVersion: Long? = null,
) {
    /** Whether this receipt is about [localContentVersion] or a later version of it. */
    fun covers(localContentVersion: Long): Boolean = contentVersion >= localContentVersion
}

@Serializable
data class ReceiptsResponse(
    val schema: Int = WearProtocol.RECEIPTS_SCHEMA,
    val cloud: CloudPresence,
    val receipts: List<Receipt>,
    val context: PeerDeliveryContext? = null,
)
