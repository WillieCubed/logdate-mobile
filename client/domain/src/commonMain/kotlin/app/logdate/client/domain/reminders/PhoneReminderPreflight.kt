package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.toStreakDay
import kotlin.time.Clock

/** Cheap gates checked before journal, draft, health or recall data is collected. */
class PhoneReminderPreflight(
    private val environment: suspend () -> PhoneReminderEnvironment,
    private val budget: DeviceReminderBudget,
    private val history: ReminderPostedHistory,
    private val clock: Clock = Clock.System,
) {
    suspend fun check(): PhoneReminderOutcome? {
        val current = environment()
        if (!current.enabled) return PhoneReminderOutcome.SKIPPED
        if (current.audience.isBlank()) return PhoneReminderOutcome.HELD
        val day = clock.now().toStreakDay(current.zone, current.dayStartHour)
        if (history.latest()?.toStreakDay(current.zone, current.dayStartHour) == day) return PhoneReminderOutcome.SKIPPED
        if (!budget.available(day)) return PhoneReminderOutcome.HELD
        return null
    }
}
