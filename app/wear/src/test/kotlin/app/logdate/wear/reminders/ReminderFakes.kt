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
    override val enabled = MutableStateFlow(enabled)

    override fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override fun lastReminderAt(): Instant? = last

    override fun setLastReminderAt(at: Instant) {
        last = at
    }
}
