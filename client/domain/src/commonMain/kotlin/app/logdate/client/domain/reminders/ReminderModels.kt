package app.logdate.client.domain.reminders

import app.logdate.client.domain.activity.AgreedActivity
import app.logdate.client.domain.streak.DEFAULT_STREAK_DAY_START_HOUR
import app.logdate.client.domain.streak.FirePhase
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The local times a person is usually awake, from their average wake time to their average bedtime.
 * A bedtime earlier than the wake time falls after midnight.
 */
data class WakingHours(
    val wake: LocalTime,
    val sleep: LocalTime,
) {
    companion object {
        /** Used for whichever average is not known yet. */
        val Fallback = WakingHours(wake = LocalTime(7, 30), sleep = LocalTime(22, 30))

        /** Waking hours from the averages that are known; equal times describe no window, so they fall back. */
        fun from(
            averageWake: LocalTime?,
            averageSleep: LocalTime?,
        ): WakingHours {
            val hours = WakingHours(wake = averageWake ?: Fallback.wake, sleep = averageSleep ?: Fallback.sleep)
            if (hours.wake == hours.sleep) return Fallback
            return hours
        }
    }
}

/**
 * The moment of a reminder decision and the bounds of the person's day.
 *
 * @property dayStartHour The hour a streak day starts, as the campfire counts it.
 * @property lastReminderAt The later of the two devices' last reminders, as the state documents merge it.
 */
data class ReminderClock(
    val now: Instant,
    val zone: TimeZone,
    val wakingHours: WakingHours = WakingHours.Fallback,
    val dayStartHour: Int = DEFAULT_STREAK_DAY_START_HOUR,
    val lastReminderAt: Instant? = null,
)

/**
 * What is happening, as the signal sources observe it.
 *
 * @property enabled The reminders switch shared with the watch.
 * @property writtenToday Whether anything was logged in the current streak day; a mark counts once it is filled in.
 * @property openMarks Marks still waiting to be filled in.
 * @property campfire Today's fire, or null when streak tracking is off.
 * @property newPlaceArrivedAt When the person arrived at a place new to them, while they are still there.
 * @property workoutEndedAt When the latest workout ended, from a watch without activity awareness.
 * @property activity What the person is doing now and what they last finished, while activity
 *   awareness is on.
 */
data class ReminderSignals(
    val enabled: Boolean,
    val writtenToday: Boolean,
    val openMarks: Int = 0,
    val campfire: FirePhase? = null,
    val newPlaceArrivedAt: Instant? = null,
    val workoutEndedAt: Instant? = null,
    val draftWaiting: Boolean = false,
    val memoryAvailable: Boolean = false,
    val activity: AgreedActivity = AgreedActivity(),
)

/** What a reminder rings for, in the order [ReminderOrchestrator] prefers them. */
@Serializable
enum class ReminderTrigger {
    /** A walk, run, ride or workout ended. Sent only to a watch that advertises activity reminders. */
    MOVING_ENDED,

    /** A trip in a vehicle ended. Sent only to a watch that advertises activity reminders. */
    TRIP_ENDED,
    WORKOUT_ENDED,
    ARRIVED_SOMEWHERE_NEW,
    CAMPFIRE_EMBERS,
    WIND_DOWN,
    DRAFT_RESCUE,
    MEMORY_RECALL,
}

enum class SkipReason {
    REMINDERS_OFF,
    OUTSIDE_WAKING_HOURS,
    ALREADY_REMINDED_TODAY,
    NOTHING_TO_REMIND,

    /** The person is moving or in a vehicle; the reminder waits until they stop. */
    ACTIVITY_UNDER_WAY,
}

sealed interface ReminderDecision {
    /** Ring now for [trigger]; [openMarks] lets the reminder add how many marks wait to be filled in. */
    data class Deliver(
        val trigger: ReminderTrigger,
        val openMarks: Int,
    ) : ReminderDecision

    data class Skip(
        val reason: SkipReason,
    ) : ReminderDecision
}
