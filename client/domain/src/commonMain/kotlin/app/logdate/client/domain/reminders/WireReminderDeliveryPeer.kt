package app.logdate.client.domain.reminders

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.PeerRole
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Null is an uncertain outcome, so callers must not post a fallback notification. */
class WireReminderDeliveryPeer(
    private val exchange: suspend (node: String, path: String, request: ByteArray) -> ByteArray?,
) {
    suspend fun deliver(request: ReminderDeliveryRequest): ReminderDeliveryStatus? {
        if (request.schema != 1 ||
            request.claim.role != PeerRole.PHONE ||
            !request.claim.context.isValid() ||
            request.openMarks < 0
        ) {
            return null
        }
        val payload = WearJson.encodeToBytes(ReminderDeliveryRequest.serializer(), request)
        if (payload.size > WearProtocol.MAX_STATE_BYTES) return null
        val bytes =
            try {
                withTimeoutOrNull(10_000) { exchange(request.claim.context.watchNode, WearPaths.REMINDERS_DELIVER, payload) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("The watch's reminder delivery outcome is unknown", e)
                null
            } ?: return null
        if (bytes.size > WearProtocol.MAX_STATE_BYTES) return null
        val decoded = WearJson.decode(bytes, 1, ReminderDeliveryResponse.serializer()) as? Decoded.Supported ?: return null
        return decoded.value.takeIf { it.claim == request.claim }?.status
    }
}
