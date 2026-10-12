package app.logdate.client.sync.datalayer.v2

import app.logdate.client.sync.peer.state.Presence
import kotlinx.serialization.Serializable

@Serializable
data class PresenceRequest(
    val schema: Int = 1,
    val context: PeerDeliveryContext,
)

@Serializable
data class PresenceResponse(
    val schema: Int = 1,
    val context: PeerDeliveryContext,
    val presence: Presence,
)
