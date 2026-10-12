package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable

object WearMediaPaths {
    const val INFO = "/logdate/v2/media/info"
    const val RANGE = "/logdate/v2/media/range"
    const val CHANNEL_PREFIX = "/logdate/v2/p2w/media/"
}

@Serializable
data class PhoneMediaDescriptor(
    val schema: Int = 1,
    val context: PeerDeliveryContext,
    val noteId: String,
    val contentVersion: Long,
    val sizeBytes: Long,
    val sha256: String,
    val mediaRef: String? = null,
)

@Serializable
data class PhoneMediaInfoRequest(
    val schema: Int = 1,
    val context: PeerDeliveryContext,
    val noteId: String,
    val contentVersion: Long,
)

@Serializable
data class PhoneMediaRangeRequest(
    val schema: Int = 1,
    val media: PhoneMediaDescriptor,
    val offset: Long,
)

@Serializable
data class PhoneMediaRangeTicket(
    val schema: Int = 1,
    val media: PhoneMediaDescriptor,
    val offset: Long,
    val channelPath: String,
)
