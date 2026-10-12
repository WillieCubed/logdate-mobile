package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.toStreakDay
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.state.Presence
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** A fresh decision snapshot; audience changes invalidate work already in flight. */
data class PhoneReminderSnapshot(
    val clock: ReminderClock,
    val signals: ReminderSignals,
    val audience: String,
    val watch: ReminderWatch? = null,
)

data class ReminderWatch(
    val context: PeerDeliveryContext,
    val reachable: Boolean,
    val supportsReminders: Boolean,
)

enum class PhoneReminderOutcome { SKIPPED, HELD, PHONE_POSTED, WATCH_POSTED, UNCERTAIN }

fun interface PhoneReminderSnapshotSource {
    suspend fun read(): PhoneReminderSnapshot
}

class PhoneReminderDelivery(
    private val budget: DeviceReminderBudget,
    private val history: ReminderPostedHistory,
    private val claims: CoordinatedReminderClaims,
    private val presence: WireWatchPresence,
    private val watchDelivery: WireReminderDeliveryPeer,
    private val snapshot: PhoneReminderSnapshotSource,
    private val isCurrent: suspend (PeerDeliveryContext) -> Boolean,
    private val postPhone: (ReminderTrigger, Int, LocalDate) -> Boolean,
    private val clock: Clock = Clock.System,
) {
    suspend fun run(): PhoneReminderOutcome {
        val initial = snapshot.read()
        if (initial.audience.isBlank()) return PhoneReminderOutcome.HELD
        if (decision(initial) !is ReminderDecision.Deliver) return PhoneReminderOutcome.SKIPPED
        val day = initial.day()
        if (!budget.available(day)) return PhoneReminderOutcome.HELD
        val watch = initial.watch?.takeIf { it.supportsReminders }
        if (watch != null && !isCurrent(watch.context)) return PhoneReminderOutcome.HELD
        val claim = watch?.let { claims.tryClaim(it.context) ?: return PhoneReminderOutcome.HELD }
        if (claim != null && claim.day != day) return PhoneReminderOutcome.HELD
        val reading = if (watch?.reachable == true) presence.sample(watch.context) else Presence()
        val confirmed = fresh(initial) ?: return PhoneReminderOutcome.HELD
        if (!budget.consume(day)) return PhoneReminderOutcome.HELD
        val final = fresh(initial) ?: return PhoneReminderOutcome.HELD
        val trigger = decision(final) as? ReminderDecision.Deliver ?: return PhoneReminderOutcome.HELD
        val target =
            NudgeTargetPolicy.choose(
                confirmed.watch?.reachable == true && final.watch?.reachable == true,
                watch != null,
                reading,
                final.clock.now.toEpochMilliseconds(),
            )
        if (target == NudgeTarget.WATCH && claim != null) {
            return when (
                watchDelivery.deliver(
                    ReminderDeliveryRequest(claim = claim, trigger = trigger.trigger, openMarks = trigger.openMarks),
                )
            ) {
                ReminderDeliveryStatus.POSTED -> {
                    history.recordPostedAt(clock.now())
                    PhoneReminderOutcome.WATCH_POSTED
                }
                ReminderDeliveryStatus.NOT_POSTED -> {
                    val fallback = fresh(initial) ?: return PhoneReminderOutcome.HELD
                    post(fallback, day)
                }
                ReminderDeliveryStatus.ALREADY_HANDLED -> PhoneReminderOutcome.HELD
                null -> PhoneReminderOutcome.UNCERTAIN
            }
        }
        return post(final, day)
    }

    private suspend fun post(
        state: PhoneReminderSnapshot,
        day: LocalDate,
    ): PhoneReminderOutcome {
        val delivery = decision(state) as? ReminderDecision.Deliver ?: return PhoneReminderOutcome.HELD
        if (!postPhone(delivery.trigger, delivery.openMarks, day)) return PhoneReminderOutcome.HELD
        history.recordPostedAt(clock.now())
        return PhoneReminderOutcome.PHONE_POSTED
    }

    private suspend fun fresh(initial: PhoneReminderSnapshot): PhoneReminderSnapshot? {
        val current = snapshot.read()
        val peer = current.watch?.takeIf { it.supportsReminders }?.context
        val expected = initial.watch?.takeIf { it.supportsReminders }?.context
        if (current.audience != initial.audience || current.day() != initial.day() || peer != expected) return null
        if (peer != null && !isCurrent(peer)) return null
        return current.takeIf { decision(it) is ReminderDecision.Deliver }
    }

    private suspend fun decision(state: PhoneReminderSnapshot): ReminderDecision =
        ReminderOrchestrator.decide(
            state.clock.copy(lastReminderAt = listOfNotNull(state.clock.lastReminderAt, history.latest()).maxOrNull()),
            state.signals,
        )

    private fun PhoneReminderSnapshot.day() = clock.now.toStreakDay(clock.zone, clock.dayStartHour)
}
