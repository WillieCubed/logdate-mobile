package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.FirePhase
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** One row of a decision table: the clock and signals, and the decision they must produce. */
internal data class ReminderCase(
    val name: String,
    val clock: ReminderClock,
    val signals: ReminderSignals,
    val expected: ReminderDecision,
)

internal val denver: TimeZone = TimeZone.of("America/Denver")

/** A Wednesday in October, when Denver is on daylight time. */
internal val day: LocalDate = LocalDate(2026, 10, 14)

internal fun at(
    hour: Int,
    minute: Int,
    date: LocalDate = day,
): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(denver)

internal fun nextDay(
    hour: Int,
    minute: Int,
): Instant = at(hour, minute, day.plus(1, DateTimeUnit.DAY))

internal fun hours(
    wakeHour: Int,
    wakeMinute: Int,
    sleepHour: Int,
    sleepMinute: Int,
) = WakingHours(LocalTime(wakeHour, wakeMinute), LocalTime(sleepHour, sleepMinute))

internal fun clock(
    now: Instant,
    wakingHours: WakingHours = WakingHours.Fallback,
    dayStartHour: Int = 4,
    lastReminderAt: Instant? = null,
) = ReminderClock(now = now, zone = denver, wakingHours = wakingHours, dayStartHour = dayStartHour, lastReminderAt = lastReminderAt)

/** Reminders on, something written, the fire burning: nothing to remind about. */
internal val quiet = ReminderSignals(enabled = true, writtenToday = true, campfire = FirePhase.BURNING)

/** [quiet] plus a workout that ended five minutes before [now], so only the window and the daily limit decide. */
internal fun workoutJustEnded(now: Instant) = quiet.copy(workoutEndedAt = now - 5.minutes)

internal val deliverWorkout = ReminderDecision.Deliver(ReminderTrigger.WORKOUT_ENDED, openMarks = 0)

internal fun skip(reason: SkipReason) = ReminderDecision.Skip(reason)

internal fun assertTable(cases: List<ReminderCase>) {
    cases.forEach { case ->
        assertEquals(case.expected, ReminderOrchestrator.decide(case.clock, case.signals), case.name)
    }
}
