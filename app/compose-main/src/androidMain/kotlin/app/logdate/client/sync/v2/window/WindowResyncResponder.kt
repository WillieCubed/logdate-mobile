package app.logdate.client.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.datalayer.v2.WindowResyncRequest
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers

/** Answers only the verified watch that owns the requested window. */
class WindowResyncResponder(
    private val publisher: WatchWindowPublisher,
    private val peers: PhoneDeliveryPeers,
) {
    suspend fun answer(
        nodeId: String,
        payload: ByteArray,
    ): ByteArray? {
        val request =
            (WearJson.decode(payload, WearProtocol.WINDOW_SCHEMA, WindowResyncRequest.serializer()) as? Decoded.Supported)?.value
                ?: return null
        val peer = peers.current(nodeId) ?: return null
        if (request.context != peer) return null
        val published = publisher.publishIfActive(nodeId) ?: return null
        if (published.context != peer || !peers.isCurrent(peer)) return null
        return WearJson.encodeToBytes(
            WindowResyncResponse.serializer(),
            WindowResyncResponse(context = peer, members = published.members, publishedAt = published.publishedAt),
        )
    }
}
