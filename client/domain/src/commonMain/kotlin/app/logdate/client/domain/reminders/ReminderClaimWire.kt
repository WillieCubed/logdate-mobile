package app.logdate.client.domain.reminders

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.PeerRole
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data class ReminderClaimRequest(
    val schema: Int = 1,
    val claim: ReminderDayClaim,
)

@Serializable
data class ReminderClaimResponse(
    val schema: Int = 1,
    val claim: ReminderDayClaim,
    val granted: Boolean,
)

class ReminderClaimResponder(
    private val claims: CoordinatedReminderClaims,
    private val role: PeerRole,
) {
    suspend fun answer(
        sourceNode: String,
        request: ByteArray,
    ): ByteArray? {
        if (request.size > WearProtocol.MAX_STATE_BYTES) return null
        val decoded = WearJson.decode(request, 1, ReminderClaimRequest.serializer()) as? Decoded.Supported ?: return null
        val claim = decoded.value.claim
        if (claim.role == role) return null
        val source = if (claim.role == PeerRole.PHONE) claim.context.phoneNode else claim.context.watchNode
        if (sourceNode != source) return null
        val granted = claims.grant(claim)
        return WearJson.encodeToBytes(ReminderClaimResponse.serializer(), ReminderClaimResponse(claim = claim, granted = granted))
    }
}

class WireReminderClaimPeer(
    private val role: PeerRole,
    private val exchange: suspend (node: String, path: String, request: ByteArray) -> ByteArray?,
) : ReminderClaimPeer {
    override suspend fun request(claim: ReminderDayClaim): Boolean? {
        if (claim.role != role || !claim.context.isValid()) return false
        val destination = if (role == PeerRole.PHONE) claim.context.watchNode else claim.context.phoneNode
        val request = WearJson.encodeToBytes(ReminderClaimRequest.serializer(), ReminderClaimRequest(claim = claim))
        val bytes =
            try {
                exchange(destination, WearPaths.REMINDERS_CLAIM, request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("The paired device could not answer the daily reminder claim", e)
                null
            } ?: return null
        if (bytes.size > WearProtocol.MAX_STATE_BYTES) return false
        val decoded = WearJson.decode(bytes, 1, ReminderClaimResponse.serializer()) as? Decoded.Supported ?: return false
        return decoded.value.claim == claim && decoded.value.granted
    }
}
