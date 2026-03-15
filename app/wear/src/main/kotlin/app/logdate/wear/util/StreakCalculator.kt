package app.logdate.wear.util

import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Calculates the current journaling streak (consecutive days with at least one entry).
 *
 * If today has no entries yet, the streak is counted from yesterday backward,
 * giving the user until end of day to maintain it. If neither today nor yesterday
 * has entries, the streak is 0.
 */
class StreakCalculator(
    private val repository: JournalNotesRepository,
) {
    suspend fun calculateStreak(
        today: LocalDate = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date,
    ): Int {
        val todayHasEntries = repository.getNotesForDay(today).isNotEmpty()

        val startDate = if (todayHasEntries) {
            today
        } else {
            val yesterday = today.minus(1, DateTimeUnit.DAY)
            if (repository.getNotesForDay(yesterday).isNotEmpty()) {
                yesterday
            } else {
                return 0
            }
        }

        var streak = 1
        var checkDate = startDate.minus(1, DateTimeUnit.DAY)

        while (streak < MAX_LOOKBACK_DAYS) {
            if (repository.getNotesForDay(checkDate).isEmpty()) {
                break
            }
            streak++
            checkDate = checkDate.minus(1, DateTimeUnit.DAY)
        }

        return streak
    }

    companion object {
        const val MAX_LOOKBACK_DAYS = 365
    }
}
