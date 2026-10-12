package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.DEFAULT_STREAK_DAY_START_HOUR
import app.logdate.client.domain.streak.toStreakDay
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Null means the peer cannot be reached; a refusal must be false. */
fun interface ReminderClaimPeer {
    suspend fun request(claim: ReminderDayClaim): Boolean?
}

class CoordinatedReminderClaims(
    private val claims: ReminderDayClaims,
    private val role: PeerRole,
    private val peer: ReminderClaimPeer,
    private val isCurrent: suspend (PeerDeliveryContext) -> Boolean,
    private val enabled: suspend () -> Boolean,
    private val clock: Clock = Clock.System,
    private val timeZone: suspend () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val dayStartHour: suspend () -> Int = { DEFAULT_STREAK_DAY_START_HOUR },
) {
    suspend fun tryClaim(context: PeerDeliveryContext): ReminderDayClaim? {
        if (!context.isValid() || !enabled() || !isCurrent(context)) return null
        val claim = ReminderDayClaim(context, today(), Uuid.random().toString(), role)
        if (!claims.begin(claim)) return null
        val granted = withTimeoutOrNull(5_000) { peer.request(claim) }
        if (granted == false || !canUse(claim) || !claims.commit(claim)) return null
        return claim.takeIf { canUse(it) }
    }

    suspend fun grant(claim: ReminderDayClaim): Boolean {
        if (claim.role == role || !canUse(claim) || !claims.grantPeer(claim)) return false
        return canUse(claim)
    }

    suspend fun acceptsDelegated(claim: ReminderDayClaim): Boolean = claim.role != role && canUse(claim)

    suspend fun consumeDelegated(claim: ReminderDayClaim): Boolean =
        acceptsDelegated(claim) && claims.consumePeer(claim) && acceptsDelegated(claim)

    private suspend fun canUse(claim: ReminderDayClaim): Boolean =
        claim.context.isValid() && claim.day == today() && enabled() && isCurrent(claim.context)

    private suspend fun today() = clock.now().toStreakDay(timeZone(), dayStartHour())
}
