package app.logdate.wear.reminders

import app.logdate.client.domain.streak.toStreakDay
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * When the watch may remind: inside the waking window, at most once a streak day, and for a workout,
 * within [WORKOUT_WINDOW] of its end. The waking window is the fallback the reminder engine uses
 * until it learns the person's own hours.
 */
object WatchReminderRules {
    val WORKOUT_WINDOW: Duration = 30.minutes
    val WAKING_START: LocalTime = LocalTime(7, 30)
    val WAKING_END: LocalTime = LocalTime(22, 30)

    fun isWaking(
        now: Instant,
        timeZone: TimeZone,
    ): Boolean {
        val time = now.toLocalDateTime(timeZone).time
        return time >= WAKING_START && time < WAKING_END
    }

    fun remindedToday(
        now: Instant,
        lastReminderAt: Instant?,
        timeZone: TimeZone,
    ): Boolean = lastReminderAt != null && lastReminderAt.toStreakDay(timeZone) == now.toStreakDay(timeZone)

    fun workoutReminderDue(
        now: Instant,
        endedAt: Instant,
        lastReminderAt: Instant?,
        timeZone: TimeZone,
    ): Boolean =
        endedAt <= now &&
            now - endedAt <= WORKOUT_WINDOW &&
            isWaking(now, timeZone) &&
            !remindedToday(now, lastReminderAt, timeZone)

    val EVENING_CHECK: LocalTime = LocalTime(20, 30)

    fun eveningReminderDue(
        now: Instant,
        lastReminderAt: Instant?,
        momentsToday: Int,
        phoneReachable: Boolean,
        timeZone: TimeZone,
    ): Boolean =
        !phoneReachable &&
            momentsToday == 0 &&
            now.toLocalDateTime(timeZone).time >= EVENING_CHECK &&
            isWaking(now, timeZone) &&
            !remindedToday(now, lastReminderAt, timeZone)
}
