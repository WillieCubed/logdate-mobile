package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ReceiptQuery(
    val kind: RecordKind,
    val id: String,
    val contentVersion: Long,
    val operationId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val removed: Boolean = false,
)

/** Sent with `MessageClient.sendRequest` to [WearPaths.RECEIPTS]. */
@Serializable
data class ReceiptsRequest(
    val schema: Int = WearProtocol.RECEIPTS_SCHEMA,
    val items: List<ReceiptQuery>,
    val context: PeerDeliveryContext? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Receipt(
    val kind: RecordKind,
    val id: String,
    val contentVersion: Long,
    val state: ReceiptState,
    val serverVersion: Long? = null,
    val operationId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val removed: Boolean = false,
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
