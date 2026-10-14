package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.Serializable

object WatchDiagnosticsProtocol {
    const val PATH = "/logdate/diag/ping"
}

@Serializable
data class WatchDiagnosticsRequest(
    val schema: Int = 1,
    val context: PeerDeliveryContext,
    val nonce: String,
)

@Serializable
data class WatchDiagnosticsResponse(
    val schema: Int = 1,
    val context: PeerDeliveryContext,
    val nonce: String,
    val notificationsAllowed: Boolean,
    val microphoneAllowed: Boolean,
)

/** Answers only for the currently verified phone, checking again after reading permissions. */
class WatchDiagnosticsResponder(
    private val current: suspend () -> PeerDeliveryContext?,
    private val enabled: suspend () -> Boolean,
    private val notificationsAllowed: () -> Boolean,
    private val microphoneAllowed: () -> Boolean,
) {
    suspend fun answer(
        source: String,
        payload: ByteArray,
    ): ByteArray? {
        if (payload.size > WearProtocol.MAX_STATE_BYTES || !enabled()) return null
        val request = (WearJson.decode(payload, 1, WatchDiagnosticsRequest.serializer()) as? Decoded.Supported)?.value ?: return null
        val peer = request.context
        if (!peer.isValid() ||
            request.nonce.isBlank() ||
            request.nonce.length > 100 ||
            source != peer.phoneNode ||
            current() != peer
        ) {
            return null
        }
        val response =
            WatchDiagnosticsResponse(
                context = peer,
                nonce = request.nonce,
                notificationsAllowed = notificationsAllowed(),
                microphoneAllowed = microphoneAllowed(),
            )
        if (!enabled() || current() != peer) return null
        return WearJson.encodeToBytes(WatchDiagnosticsResponse.serializer(), response)
    }
}
