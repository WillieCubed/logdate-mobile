package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.nextStreakDayStart
import app.logdate.client.domain.streak.toStreakDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Decides whether a moment-aware reminder may ring now, and what for.
 *
 * Pure: the same clock and signals always give the same decision, so delivery can ask again whenever
 * a signal changes. A reminder rings only with reminders on, only inside the person's waking hours,
 * at most once per streak day across both devices, and for the first of these that applies:
 * 1. a workout that ended within the last [WORKOUT_RECENCY];
 * 2. arriving somewhere new and staying at least [ARRIVAL_DWELL];
 * 3. a campfire in embers with nothing logged today and at most [CAMPFIRE_LAST_STRETCH] left before
 *    bedtime or the end of the streak day, whichever comes first;
 * 4. the evening wind-down, the last [WIND_DOWN_LEAD] before bedtime, when nothing was written today
 *    or marks wait to be filled in.
 */
object ReminderOrchestrator {
    val WORKOUT_RECENCY: Duration = 30.minutes
    val ARRIVAL_DWELL: Duration = 10.minutes
    val CAMPFIRE_LAST_STRETCH: Duration = 3.hours
    val WIND_DOWN_LEAD: Duration = 90.minutes

    fun decide(
        clock: ReminderClock,
        signals: ReminderSignals,
    ): ReminderDecision {
        if (!signals.enabled) return ReminderDecision.Skip(SkipReason.REMINDERS_OFF)
        val window =
            WakingWindow.containing(clock.now, clock.zone, clock.wakingHours)
                ?: return ReminderDecision.Skip(SkipReason.OUTSIDE_WAKING_HOURS)
        if (remindedThisStreakDay(clock)) return ReminderDecision.Skip(SkipReason.ALREADY_REMINDED_TODAY)
        val trigger = firstTrigger(clock, signals, window) ?: return ReminderDecision.Skip(SkipReason.NOTHING_TO_REMIND)
        return ReminderDecision.Deliver(trigger, signals.openMarks)
    }

    private fun remindedThisStreakDay(clock: ReminderClock): Boolean {
        val last = clock.lastReminderAt ?: return false
        return last.toStreakDay(clock.zone, clock.dayStartHour) == clock.now.toStreakDay(clock.zone, clock.dayStartHour)
    }

    private fun firstTrigger(
        clock: ReminderClock,
        signals: ReminderSignals,
        window: WakingWindow,
    ): ReminderTrigger? {
        val now = clock.now
        if (signals.workoutEndedAt.endedWithin(now, WORKOUT_RECENCY)) return ReminderTrigger.WORKOUT_ENDED
        if (signals.newPlaceArrivedAt.lastedAtLeast(now, ARRIVAL_DWELL)) return ReminderTrigger.ARRIVED_SOMEWHERE_NEW
        if (campfireFading(clock, signals, window)) return ReminderTrigger.CAMPFIRE_EMBERS
        if (windingDown(now, signals, window)) return ReminderTrigger.WIND_DOWN
        if (signals.draftWaiting) return ReminderTrigger.DRAFT_RESCUE
        if (signals.memoryAvailable) return ReminderTrigger.MEMORY_RECALL
        return null
    }

    private fun campfireFading(
        clock: ReminderClock,
        signals: ReminderSignals,
        window: WakingWindow,
    ): Boolean {
        if (signals.campfire != FirePhase.EMBERS || signals.writtenToday) return false
        val streakDayEnds = nextStreakDayStart(clock.now, clock.zone, clock.dayStartHour)
        return minOf(window.end, streakDayEnds) - clock.now <= CAMPFIRE_LAST_STRETCH
    }

    private fun windingDown(
        now: Instant,
        signals: ReminderSignals,
        window: WakingWindow,
    ): Boolean {
        if (now < window.end - WIND_DOWN_LEAD) return false
        return !signals.writtenToday || signals.openMarks > 0
    }

    /** True when this instant is no later than [now] and at most [limit] before it. */
    private fun Instant?.endedWithin(
        now: Instant,
        limit: Duration,
    ): Boolean {
        if (this == null || this > now) return false
        return now - this <= limit
    }

    /** True when this instant is no later than [now] and at least [minimum] before it. */
    private fun Instant?.lastedAtLeast(
        now: Instant,
        minimum: Duration,
    ): Boolean {
        if (this == null || this > now) return false
        return now - this >= minimum
    }
}

/** The waking hours that contain an instant, as instants, so a bedtime after midnight and DST both work. */
internal data class WakingWindow(
    val start: Instant,
    val end: Instant,
) {
    companion object {
        /** The window that contains [now], or null when [now] is outside waking hours. */
        fun containing(
            now: Instant,
            zone: TimeZone,
            hours: WakingHours,
        ): WakingWindow? {
            val today = now.toLocalDateTime(zone).date
            return listOf(today.minus(1, DateTimeUnit.DAY), today)
                .map { startDate -> startingOn(startDate, zone, hours) }
                .firstOrNull { window -> now >= window.start && now < window.end }
        }

        private fun startingOn(
            date: LocalDate,
            zone: TimeZone,
            hours: WakingHours,
        ): WakingWindow {
            val endDate = if (hours.sleep > hours.wake) date else date.plus(1, DateTimeUnit.DAY)
            return WakingWindow(
                start = LocalDateTime(date, hours.wake).toInstant(zone),
                end = LocalDateTime(endDate, hours.sleep).toInstant(zone),
            )
        }
    }
}
