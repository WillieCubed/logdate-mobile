package app.logdate.client.domain.activity

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What someone is doing, as the phone and the watch can tell. */
@Serializable
enum class ActivityKind(
    /** How long it has to last to count as an activity. */
    val minimum: Duration,
    /** How long its signal can stop before it counts as ended, so a red light doesn't end a drive. */
    val endGrace: Duration,
    /** Whether reminders wait until it ends. */
    val holdsReminders: Boolean,
) {
    /** Walking, running, cycling, or any exercise the watch records. */
    MOVING(minimum = 10.minutes, endGrace = 3.minutes, holdsReminders = true),

    /** In a car, bus or train. Detection cannot tell a driver from a passenger; both hold reminders. */
    IN_VEHICLE(minimum = 8.minutes, endGrace = 4.minutes, holdsReminders = true),

    /** Asleep, which only the watch can tell. */
    SLEEP(minimum = 3.hours, endGrace = 20.minutes, holdsReminders = false),
}

/** One activity; [endedAt] is null while it is still going on. */
@Serializable
data class Activity(
    val kind: ActivityKind,
    val startedAt: Instant,
    val endedAt: Instant?,
)

/** One raw signal from a device: someone started ([entered]) or stopped doing [kind] at [at]. */
data class ActivityTransition(
    val kind: ActivityKind,
    val entered: Boolean,
    val at: Instant,
)
