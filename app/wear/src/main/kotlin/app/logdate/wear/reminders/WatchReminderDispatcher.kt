package app.logdate.wear.reminders

import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** Decides whether a trigger becomes a reminder, posts it, and counts it toward the daily limit. */
class WatchReminderDispatcher(
    private val preferences: WatchReminderPreferences,
    private val notifier: NudgeNotifier,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    /** Reminds after a workout that ended at [endedAt]. @return true when a reminder was shown. */
    @Synchronized
    fun onWorkoutEnded(endedAt: Instant): Boolean {
        if (!preferences.enabled.value) return false
        val now = clock.now()
        if (!WatchReminderRules.workoutReminderDue(now, endedAt, preferences.lastReminderAt(), timeZone())) return false
        return post(WatchReminder.WORKOUT_ENDED, now)
    }

    @Synchronized
    fun onEveningCheck(
        phoneReachable: Boolean,
        momentsToday: Int,
    ): Boolean {
        if (!preferences.enabled.value) return false
        val now = clock.now()
        if (!WatchReminderRules.eveningReminderDue(
                now,
                preferences.lastReminderAt(),
                momentsToday,
                phoneReachable,
                timeZone(),
            )
        ) {
            return false
        }
        return post(WatchReminder.EVENING, now)
    }

    private fun post(
        reminder: WatchReminder,
        now: Instant,
    ): Boolean {
        if (!notifier.post(reminder)) return false
        preferences.setLastReminderAt(now)
        return true
    }
}
