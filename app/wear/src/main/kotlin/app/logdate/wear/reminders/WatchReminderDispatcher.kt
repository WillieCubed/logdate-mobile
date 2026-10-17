package app.logdate.wear.reminders

import app.logdate.client.domain.activity.Activity
import app.logdate.client.domain.activity.ActivityKind
import app.logdate.client.domain.reminders.WakingHours
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** Decides whether a trigger becomes a reminder, reserves it, then records successful posting. */
class WatchReminderDispatcher(
    private val preferences: WatchReminderPreferences,
    private val notifier: NudgeNotifier,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val authorize: suspend () -> Boolean = { true },
    private val hours: () -> WakingHours = { WakingHours.Fallback },
    private val initializeHours: suspend () -> Unit = {},
    private val currentActivity: suspend () -> Activity? = { null },
) {
    private val mutex = Mutex()

    suspend fun onWorkoutEnded(endedAt: Instant): Boolean =
        deliverIfDue(WatchReminder.WORKOUT_ENDED) { now ->
            WatchReminderRules.workoutReminderDue(now, endedAt, preferences.lastReminderAt(), timeZone(), hours())
        }

    /** A walk, ride or workout that ended can be the day's reminder; waking up is not one. */
    suspend fun onActivityEnded(activity: Activity): Boolean {
        val reminder =
            when (activity.kind) {
                ActivityKind.MOVING -> WatchReminder.MOVING_ENDED
                ActivityKind.IN_VEHICLE -> WatchReminder.TRIP_ENDED
                ActivityKind.SLEEP -> return false
            }
        return deliverIfDue(reminder) { now ->
            WatchReminderRules.activityReminderDue(now, activity, preferences.lastReminderAt(), timeZone(), hours())
        }
    }

    /** The evening reminder waits while the person is moving or in a vehicle. */
    suspend fun onEveningCheck(
        phoneReachable: Boolean,
        momentsToday: Int,
    ): Boolean {
        if (currentActivity()?.kind?.holdsReminders == true) return false
        return deliverIfDue(WatchReminder.EVENING) { now ->
            WatchReminderRules.eveningReminderDue(now, preferences.lastReminderAt(), momentsToday, phoneReachable, timeZone(), hours())
        }
    }

    private suspend fun authorized(): Boolean =
        try {
            authorize()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("The watch's daily reminder could not be reserved", e)
            false
        }

    private suspend fun deliverIfDue(
        reminder: WatchReminder,
        due: (Instant) -> Boolean,
    ): Boolean =
        mutex.withLock {
            initializeHours()
            if (!preferences.enabled.value || !due(clock.now()) || !authorized()) return@withLock false
            val now = clock.now()
            if (!preferences.enabled.value || !due(now) || !notifier.post(reminder)) return@withLock false
            preferences.setLastReminderAt(clock.now())
            true
        }
}
