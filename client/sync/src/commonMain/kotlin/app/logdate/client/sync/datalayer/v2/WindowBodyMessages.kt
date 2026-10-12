package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable

object WindowBodyPaths {
    const val RANGE = "/logdate/v2/window/body"
    const val CHANNEL_PREFIX = "/logdate/v2/p2w/window-body/"
    const val MAX_BODY_BYTES = 8L * 1024 * 1024
}

@Serializable
data class WindowBodyRequest(
    val schema: Int = 1,
    val context: PeerDeliveryContext,
    val noteId: String,
    val contentVersion: Long,
    val revision: Long,
    val body: BodyRef,
    val offset: Long = 0,
)

@Serializable
data class WindowBodyTicket(
    val schema: Int = 1,
    val request: WindowBodyRequest,
    val channelPath: String,
)
