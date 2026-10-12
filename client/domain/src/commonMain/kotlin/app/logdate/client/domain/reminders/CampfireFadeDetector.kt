package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.CampfireCalculator
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.toStreakDay
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.streak.StreakSettingsRepository
import kotlinx.coroutines.flow.first

data class ReminderJournalState(
    val writtenToday: Boolean,
    val campfire: FirePhase?,
)

/** Reads journal activity using the exact streak day of the delivery decision. */
class CampfireFadeDetector(
    private val notes: JournalNotesRepository,
    private val settings: StreakSettingsRepository,
) {
    suspend fun read(clock: ReminderClock): ReminderJournalState {
        val tracking = settings.isStreakEnabled()
        val days =
            notes
                .observeEntryTimestamps()
                .first()
                .filter { it <= clock.now }
                .mapTo(HashSet()) { it.toStreakDay(clock.zone, clock.dayStartHour) }
        val today = clock.now.toStreakDay(clock.zone, clock.dayStartHour)
        val fire = if (tracking && settings.isStreakEnabled()) CampfireCalculator.calculate(days, today).phase else null
        return ReminderJournalState(today in days, fire)
    }
}
