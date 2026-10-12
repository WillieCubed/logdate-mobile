package app.logdate.wear.reminders

import app.logdate.client.domain.reminders.WakingHours
import app.logdate.client.domain.reminders.WakingWindow
import app.logdate.client.domain.streak.toStreakDay
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The watch's offline checks use the verified phone's learned window, or the shared fallback. */
object WatchReminderRules {
    val WORKOUT_WINDOW: Duration = 30.minutes
    val EVENING_LEAD: Duration = 2.hours

    fun isWaking(
        now: Instant,
        timeZone: TimeZone,
        hours: WakingHours = WakingHours.Fallback,
    ): Boolean = WakingWindow.containing(now, timeZone, hours) != null

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
        hours: WakingHours = WakingHours.Fallback,
    ): Boolean =
        endedAt <= now &&
            now - endedAt <= WORKOUT_WINDOW &&
            isWaking(now, timeZone, hours) &&
            !remindedToday(now, lastReminderAt, timeZone)

    fun eveningReminderDue(
        now: Instant,
        lastReminderAt: Instant?,
        momentsToday: Int,
        phoneReachable: Boolean,
        timeZone: TimeZone,
        hours: WakingHours = WakingHours.Fallback,
    ): Boolean {
        if (phoneReachable || momentsToday != 0 || remindedToday(now, lastReminderAt, timeZone)) return false
        val window = WakingWindow.containing(now, timeZone, hours) ?: return false
        return now >= maxOf(window.start, window.end - EVENING_LEAD)
    }
}
