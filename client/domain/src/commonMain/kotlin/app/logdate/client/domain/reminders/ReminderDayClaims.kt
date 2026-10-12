package app.logdate.client.domain.reminders

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ReminderDayClaim(
    val context: PeerDeliveryContext,
    val day: LocalDate,
    val id: String,
    val role: PeerRole,
)

/** A durable reservation is kept even if the process stops before notification posting. */
class ReminderDayClaims(
    private val storage: KeyValueStorage,
    private val role: PeerRole,
) {
    private val mutex = Mutex()

    suspend fun begin(claim: ReminderDayClaim): Boolean =
        mutex.withLock {
            if (!claim.valid() || claim.role != role) return@withLock false
            val current = read(claim.context)
            if (current != null && current.claim.day >= claim.day) return@withLock false
            save(Reservation(claim = claim, phase = Phase.PENDING))
            true
        }

    suspend fun grantPeer(claim: ReminderDayClaim): Boolean =
        mutex.withLock {
            if (!claim.valid() || claim.role == role) return@withLock false
            val current = read(claim.context)
            if (current != null) {
                if (current.claim.day > claim.day) return@withLock false
                if (current.claim.day == claim.day) {
                    if (current.claim == claim && current.phase == Phase.RESERVED) return@withLock true
                    val phoneWinsPendingRace = current.phase == Phase.PENDING && role == PeerRole.WATCH && claim.role == PeerRole.PHONE
                    if (!phoneWinsPendingRace) return@withLock false
                }
            }
            save(Reservation(claim = claim, phase = Phase.RESERVED))
            true
        }

    /** Only the process that wins this transition may post. A restart cannot post the same claim again. */
    suspend fun commit(claim: ReminderDayClaim): Boolean =
        mutex.withLock {
            if (!claim.valid() || claim.role != role) return@withLock false
            val current = read(claim.context) ?: return@withLock false
            if (current.claim != claim || current.phase != Phase.PENDING) return@withLock false
            save(current.copy(phase = Phase.RESERVED))
            true
        }

    private fun ReminderDayClaim.valid(): Boolean = context.isValid() && Uuid.parseOrNull(id) != null

    private suspend fun read(context: PeerDeliveryContext): Reservation? {
        val text = storage.getString(key(context)) ?: return null
        val reservation = WearJson.json.decodeFromString(Reservation.serializer(), text)
        check(reservation.schema == 1 && reservation.claim.valid()) { "The stored reminder reservation is unreadable" }
        return reservation
    }

    private suspend fun save(reservation: Reservation) {
        storage.putString(key(reservation.claim.context), WearJson.encode(Reservation.serializer(), reservation))
    }

    private fun key(context: PeerDeliveryContext): String {
        val audience =
            when (context.mode) {
                LinkMode.PAIR -> Audience(phoneNode = context.phoneNode)
                LinkMode.CLOUD -> Audience(accountId = context.accountId, serverOrigin = context.serverOrigin)
            }
        return "reminder_day_claim_v1:${WearJson.encode(Audience.serializer(), audience)}"
    }

    @Serializable
    private data class Audience(
        val phoneNode: String? = null,
        val accountId: String? = null,
        val serverOrigin: String? = null,
    )

    @Serializable
    private enum class Phase { PENDING, RESERVED }

    @Serializable
    private data class Reservation(
        val schema: Int = 1,
        val claim: ReminderDayClaim,
        val phase: Phase,
    )
}
