package app.logdate.client.domain.reminders

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Tests when [ReminderOrchestrator] may ring at all: only with reminders on, only inside the person's
 * waking hours (their average wake time to their average bedtime, falling back to 7:30 to 22:30), and
 * at most once per streak day. A workout just ended in every row, so only these rules decide.
 */
class ReminderWindowTest {
    private val outside = skip(SkipReason.OUTSIDE_WAKING_HOURS)
    private val alreadyReminded = skip(SkipReason.ALREADY_REMINDED_TODAY)
    private val earlyBird = hours(6, 0, 23, 30)
    private val lateRiser = hours(10, 0, 1, 30)
    private val yesterday = day.minus(1, DateTimeUnit.DAY)
    private val springForward = LocalDate(2026, 3, 8)
    private val fallBack = LocalDate(2026, 11, 1)

    private fun case(
        name: String,
        now: Instant,
        expected: ReminderDecision,
        wakingHours: WakingHours = WakingHours.Fallback,
        dayStartHour: Int = 4,
        lastReminderAt: Instant? = null,
        enabled: Boolean = true,
    ) = ReminderCase(name, clock(now, wakingHours, dayStartHour, lastReminderAt), workoutJustEnded(now).copy(enabled = enabled), expected)

    private val wakingHoursTable =
        listOf(
            case("fallback window opens at 7:30", at(7, 30), deliverWorkout),
            case("a minute before the fallback window", at(7, 29), outside),
            case("last minute of the fallback window", at(22, 29), deliverWorkout),
            case("fallback window closes at 22:30", at(22, 30), outside),
            case("an earlier average wake time opens the window", at(6, 15), deliverWorkout, earlyBird),
            case("a later average bedtime leaves the window open", at(23, 0), deliverWorkout, earlyBird),
            case("after the average bedtime", at(23, 45), outside, earlyBird),
            case("a bedtime after midnight extends the window", nextDay(0, 30), deliverWorkout, lateRiser),
            case("after a bedtime past midnight", nextDay(2, 0), outside, lateRiser),
            case("before a late wake time", at(9, 30), outside, lateRiser),
        )

    private val dailyLimitTable =
        listOf(
            case("a reminder earlier in the streak day blocks another", at(15, 0), alreadyReminded, lastReminderAt = at(9, 0)),
            case("a reminder before the day start belongs to the day before", at(9, 0), deliverWorkout, lastReminderAt = at(3, 0)),
            case("a reminder yesterday evening does not block today", at(9, 0), deliverWorkout, lastReminderAt = at(21, 0, yesterday)),
            case("a later day start moves the boundary", at(10, 30), deliverWorkout, dayStartHour = 10, lastReminderAt = at(9, 30)),
            case("after midnight is still the same streak day", nextDay(0, 30), alreadyReminded, lateRiser, lastReminderAt = at(23, 0)),
            case("reminders off", at(12, 0), skip(SkipReason.REMINDERS_OFF), enabled = false),
            case("reminders off outside waking hours", at(3, 0), skip(SkipReason.REMINDERS_OFF), enabled = false),
            case("outside waking hours comes before the daily limit", at(23, 0), outside, lastReminderAt = at(9, 0)),
        )

    private val daylightSavingTable =
        listOf(
            case("the window opens at 7:30 on the day clocks spring forward", at(7, 30, springForward), deliverWorkout),
            case("a minute before the window on the day clocks spring forward", at(7, 29, springForward), outside),
            case("the window closes at 22:30 on the day clocks fall back", at(22, 30, fallBack), outside),
            case("the last minute on the day clocks fall back", at(22, 29, fallBack), deliverWorkout),
        )

    @Test
    fun `reminders ring only inside waking hours`() {
        assertTable(wakingHoursTable)
    }

    @Test
    fun `reminders ring at most once per streak day`() {
        assertTable(dailyLimitTable)
    }

    @Test
    fun `waking hours follow the local clock across daylight saving changes`() {
        assertTable(daylightSavingTable)
    }

    @Test
    fun `known averages are used as they are`() {
        assertEquals(earlyBird, WakingHours.from(LocalTime(6, 0), LocalTime(23, 30)))
    }

    @Test
    fun `a missing average falls back on its own`() {
        assertEquals(hours(7, 30, 23, 30), WakingHours.from(null, LocalTime(23, 30)))
        assertEquals(hours(6, 0, 22, 30), WakingHours.from(LocalTime(6, 0), null))
        assertEquals(WakingHours.Fallback, WakingHours.from(null, null))
    }

    @Test
    fun `equal averages describe no window and fall back entirely`() {
        assertEquals(WakingHours.Fallback, WakingHours.from(LocalTime(8, 0), LocalTime(8, 0)))
    }
}
