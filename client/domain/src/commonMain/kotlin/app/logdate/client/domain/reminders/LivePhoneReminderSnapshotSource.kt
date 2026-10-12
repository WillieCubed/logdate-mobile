package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.AmbientPromptCandidate
import app.logdate.client.domain.streak.toStreakDay
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** Account, consent and verified peer state captured together around asynchronous signal reads. */
data class PhoneReminderEnvironment(
    val audience: String,
    val enabled: Boolean,
    val capturePlaces: Boolean,
    val zone: TimeZone,
    val dayStartHour: Int,
    val watch: ReminderWatch? = null,
)

class LivePhoneReminderSnapshotSource(
    private val environment: suspend () -> PhoneReminderEnvironment,
    private val windDown: WindDownTimeResolver,
    private val journal: CampfireFadeDetector,
    private val arrivals: NovelPlaceArrivalDetector,
    private val openMarks: suspend () -> Int,
    private val workoutEndedAt: suspend () -> Instant? = { null },
    private val clock: Clock = Clock.System,
    private val supplemental: suspend (Instant) -> AmbientPromptCandidate? = { null },
) : PhoneReminderSnapshotSource {
    override suspend fun read(): PhoneReminderSnapshot {
        val initial = environment()
        val started = clock.now()
        val base = ReminderClock(started, initial.zone, dayStartHour = initial.dayStartHour)
        if (!initial.enabled || initial.audience.isBlank()) {
            return PhoneReminderSnapshot(base, ReminderSignals(false, false), initial.audience, initial.watch)
        }
        val waking = windDown.resolve(started, initial.zone)
        val decisionClock = base.copy(now = clock.now(), wakingHours = waking)
        val activity = journal.read(decisionClock)
        val marks = openMarks().coerceAtLeast(0)
        val arrival = if (initial.capturePlaces) arrivals.read(clock.now(), initial.audience) else null
        val workout = workoutEndedAt()
        val signals =
            ReminderSignals(
                enabled = true,
                writtenToday = activity.writtenToday,
                openMarks = marks,
                campfire = activity.campfire,
                newPlaceArrivedAt = arrival,
                workoutEndedAt = workout,
            )
        if (environment() != initial ||
            started.toStreakDay(initial.zone, initial.dayStartHour) != clock.now().toStreakDay(initial.zone, initial.dayStartHour)
        ) {
            return PhoneReminderSnapshot(decisionClock.copy(now = clock.now()), ReminderSignals(false, false), "")
        }
        val decision = ReminderOrchestrator.decide(decisionClock.copy(now = clock.now()), signals)
        val candidate = if (decision == ReminderDecision.Skip(SkipReason.NOTHING_TO_REMIND)) supplemental(clock.now()) else null
        val current = environment()
        val finalClock = decisionClock.copy(now = clock.now())
        if (current != initial ||
            started.toStreakDay(initial.zone, initial.dayStartHour) != finalClock.now.toStreakDay(initial.zone, initial.dayStartHour)
        ) {
            return PhoneReminderSnapshot(finalClock, ReminderSignals(false, false), "")
        }
        return PhoneReminderSnapshot(
            clock = finalClock,
            signals = signals,
            supplemental = candidate,
            audience = initial.audience,
            watch = initial.watch,
        )
    }
}
