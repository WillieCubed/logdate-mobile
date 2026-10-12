package app.logdate.wear.reminders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

internal val REMINDER_ZONE: TimeZone = TimeZone.of("Europe/Berlin")

internal fun berlin(
    day: Int,
    hour: Int,
    minute: Int = 0,
): Instant = LocalDateTime(2026, 10, day, hour, minute).toInstant(REMINDER_ZONE)

internal class FixedClock(
    var now: Instant,
) : Clock {
    override fun now(): Instant = now
}

internal class FakeNudgeNotifier(
    var accepts: Boolean = true,
) : NudgeNotifier {
    val posted = mutableListOf<WatchReminder>()

    override fun post(reminder: WatchReminder): Boolean {
        if (!accepts) return false
        posted += reminder
        return true
    }
}

internal class FakeReminderPreferences(
    enabled: Boolean = true,
    private var last: Instant? = null,
) : WatchReminderPreferences {
    private val peerTimes = mutableMapOf<String, Long>()

    override fun mergePhoneReminderAt(
        nodeId: String,
        at: Long,
        observedAt: Instant,
    ) {
        if (at <= 0 || at <= (peerTimes[nodeId] ?: 0)) return
        peerTimes[nodeId] = at
        val observed = Instant.fromEpochMilliseconds(minOf(at, observedAt.toEpochMilliseconds()))
        last = last?.let { maxOf(it, observed) } ?: observed
    }

    override val enabled = MutableStateFlow(enabled)

    override fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override fun lastReminderAt(): Instant? = last

    override fun setLastReminderAt(at: Instant) {
        last = at
    }
}

internal class FakeActivityRegistration(
    var supported: Boolean = true,
) : ActivityRegistration {
    var registrations = 0
    var unregistrations = 0

    override suspend fun register(): Boolean {
        if (!supported) return false
        registrations++
        return true
    }

    override suspend fun unregister() {
        unregistrations++
    }
}

internal class FakeReminderPermissions(
    var notifications: Boolean = true,
    var activityRecognition: Boolean = true,
) : ReminderPermissions {
    override fun notificationsGranted(): Boolean = notifications

    override fun activityRecognitionGranted(): Boolean = activityRecognition
}

internal class FakeTransitions : ActivityTransitionStore {
    var lastKind: ActivityKind? = null
    private var changedAt: Instant? = null

    override fun last(): ActivityKind? = lastKind

    override fun lastChangeAt(): Instant? = changedAt

    override fun record(
        kind: ActivityKind,
        changedAt: Instant,
    ) {
        lastKind = kind
        this.changedAt = changedAt
    }
}

internal class FakeEveningWork : EveningReminderWork {
    val delays = mutableListOf<Long>()
    var cancels = 0
    var ensures = 0
    var replacements = 0

    override fun ensureAfter(delayMs: Long) {
        ensures++
        delays += delayMs
    }

    override fun runAfter(
        delayMs: Long,
        replace: Boolean,
    ) {
        delays += delayMs
        if (replace) replacements++
    }

    override fun cancel() {
        cancels++
    }
}
