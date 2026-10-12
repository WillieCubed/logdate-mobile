package app.logdate.client.domain.reminders

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PresenceRequest
import app.logdate.client.sync.datalayer.v2.PresenceResponse
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.Presence
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class WireWatchPresence(
    private val exchange: suspend (node: String, path: String, request: ByteArray) -> ByteArray?,
    private val isCurrent: suspend (PeerDeliveryContext) -> Boolean,
) {
    suspend fun sample(context: PeerDeliveryContext): Presence {
        if (!context.isValid() || !isCurrent(context)) return Presence()
        val request = WearJson.encodeToBytes(PresenceRequest.serializer(), PresenceRequest(context = context))
        if (request.size > WearProtocol.MAX_STATE_BYTES) return Presence()
        val bytes =
            try {
                withTimeoutOrNull(2_000) { exchange(context.watchNode, WearPaths.PRESENCE, request) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("The watch's presence could not be checked", e)
                null
            } ?: return Presence()
        if (bytes.size > WearProtocol.MAX_STATE_BYTES || !isCurrent(context)) return Presence()
        val decoded = WearJson.decode(bytes, 1, PresenceResponse.serializer()) as? Decoded.Supported ?: return Presence()
        return decoded.value.takeIf { it.context == context }?.presence ?: Presence()
    }
}
