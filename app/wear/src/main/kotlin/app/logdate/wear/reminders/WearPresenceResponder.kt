package app.logdate.wear.reminders

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PresenceRequest
import app.logdate.client.sync.datalayer.v2.PresenceResponse
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol

class WearPresenceResponder(
    private val sampler: WearPresenceSampler,
    private val currentPeer: suspend () -> PeerDeliveryContext?,
    private val enabled: suspend () -> Boolean,
) {
    suspend fun answer(
        sourceNodeId: String,
        request: ByteArray,
    ): ByteArray? {
        if (request.size > WearProtocol.MAX_STATE_BYTES || !enabled()) return null
        val decoded = WearJson.decode(request, 1, PresenceRequest.serializer()) as? Decoded.Supported ?: return null
        val peer = decoded.value.context
        if (!peer.isValid() || sourceNodeId != peer.phoneNode || currentPeer() != peer || !enabled()) return null
        val presence = sampler.sample()
        if (!enabled() || currentPeer() != peer) return null
        return WearJson.encodeToBytes(PresenceResponse.serializer(), PresenceResponse(context = peer, presence = presence))
    }
}
