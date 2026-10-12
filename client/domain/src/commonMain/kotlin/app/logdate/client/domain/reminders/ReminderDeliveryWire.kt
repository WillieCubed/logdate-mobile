package app.logdate.client.domain.reminders

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class ReminderDeliveryRequest(
    val schema: Int = 1,
    val claim: ReminderDayClaim,
    val trigger: ReminderTrigger,
    val openMarks: Int = 0,
)

@Serializable
enum class ReminderDeliveryStatus { POSTED, NOT_POSTED, ALREADY_HANDLED }

@Serializable
data class ReminderDeliveryResponse(
    val schema: Int = 1,
    val claim: ReminderDayClaim,
    val status: ReminderDeliveryStatus,
)

class ReminderDeliveryResponder(
    private val claims: CoordinatedReminderClaims,
    private val enabled: suspend () -> Boolean,
    private val post: (ReminderTrigger, Int) -> Boolean,
    private val recordPostedAt: suspend (Instant) -> Unit,
    private val clock: Clock = Clock.System,
) {
    suspend fun answer(
        sourceNode: String,
        request: ByteArray,
    ): ByteArray? {
        if (request.size > WearProtocol.MAX_STATE_BYTES || !enabled()) return null
        val decoded = WearJson.decode(request, 1, ReminderDeliveryRequest.serializer()) as? Decoded.Supported ?: return null
        val delivery = decoded.value
        val claim = delivery.claim
        if (claim.role != PeerRole.PHONE ||
            sourceNode != claim.context.phoneNode ||
            delivery.openMarks < 0 ||
            !claims.acceptsDelegated(claim) ||
            !enabled()
        ) {
            return null
        }
        val status =
            if (!claims.consumeDelegated(claim)) {
                ReminderDeliveryStatus.ALREADY_HANDLED
            } else if (!enabled()) {
                ReminderDeliveryStatus.NOT_POSTED
            } else if (post(delivery.trigger, delivery.openMarks)) {
                recordPostedAt(clock.now())
                ReminderDeliveryStatus.POSTED
            } else {
                ReminderDeliveryStatus.NOT_POSTED
            }
        return WearJson.encodeToBytes(ReminderDeliveryResponse.serializer(), ReminderDeliveryResponse(claim = claim, status = status))
    }
}
