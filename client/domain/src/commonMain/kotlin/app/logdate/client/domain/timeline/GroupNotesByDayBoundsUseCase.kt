package app.logdate.client.domain.timeline

import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.util.LogdatePreferencesDataSource
import app.logdate.client.repository.journals.JournalNote
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Groups journal notes into "semantic days" — days as the user experiences them,
 * not as the calendar defines them.
 *
 * A person who stays up until 2 AM considers that evening part of "today," not
 * "tomorrow." Each note is placed in the day whose [DayBounds] interval contains it
 * whenever the user has a day boundary configured — either sleep-based boundaries are
 * enabled, or they have explicitly set a day-start hour. Only when neither is configured
 * do we group by calendar date (midnight boundaries).
 *
 * ## Why the previous day's bounds are fetched
 *
 * Notes recorded in the early hours (e.g., 1:30 AM on March 15) have a calendar
 * date of March 15, but may belong to March 14 if the user's day extends past
 * midnight. To catch this, bounds are fetched for both the note's calendar date
 * and the preceding day.
 *
 * ## Orphan notes
 *
 * If a note falls in a gap between two days' bounds (e.g., during the sleep
 * window itself), it is assigned to the nearest day by temporal proximity.
 */
class GroupNotesByDayBoundsUseCase(
    private val getDayBoundsUseCase: GetDayBoundsUseCase,
    private val dayBoundarySettingsRepository: DayBoundarySettingsRepository,
    private val preferencesDataSource: LogdatePreferencesDataSource,
) {
    suspend operator fun invoke(
        notes: List<JournalNote>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Map<LocalDate, List<JournalNote>> {
        if (notes.isEmpty()) return emptyMap()

        val settings = dayBoundarySettingsRepository.getSettings()
        val hasExplicitDayStartHour = preferencesDataSource.getPreferences().dayStartHour != null
        if (!settings.sleepBasedBoundariesEnabled && !hasExplicitDayStartHour) {
            return notes.groupBy { it.creationTimestamp.toLocalDateTime(timeZone).date }
        }

        return groupWithDayBounds(notes, timeZone)
    }

    private suspend fun groupWithDayBounds(
        notes: List<JournalNote>,
        timeZone: TimeZone,
    ): Map<LocalDate, List<JournalNote>> {
        val calendarDates =
            notes
                .map { it.creationTimestamp.toLocalDateTime(timeZone).date }
                .distinct()
                .sorted()

        // Also fetch the previous day — a 1:30am note may belong to yesterday
        val datesToFetch =
            buildSet {
                for (date in calendarDates) {
                    add(date.minus(1, DateTimeUnit.DAY))
                    add(date)
                }
            }.sorted()

        val boundsByDate = mutableMapOf<LocalDate, DayBounds>()
        for (date in datesToFetch) {
            getDayBoundsUseCase(date, timeZone).getOrNull()?.let { boundsByDate[date] = it }
        }

        if (boundsByDate.isEmpty()) {
            return notes.groupBy { it.creationTimestamp.toLocalDateTime(timeZone).date }
        }

        val result = mutableMapOf<LocalDate, MutableList<JournalNote>>()
        val sortedDates = boundsByDate.keys.sorted()

        for (note in notes) {
            val assignedDate = findContainingDay(note, sortedDates, boundsByDate, timeZone)
            result.getOrPut(assignedDate) { mutableListOf() }.add(note)
        }

        return result
    }

    /**
     * Finds which day's bounds contain the given note. Walks bounds chronologically
     * and returns the first `[start, end)` match. If none match (sleep gap),
     * assigns to the temporally closest day.
     */
    private fun findContainingDay(
        note: JournalNote,
        sortedDates: List<LocalDate>,
        boundsByDate: Map<LocalDate, DayBounds>,
        timeZone: TimeZone,
    ): LocalDate {
        val ts = note.creationTimestamp

        for (date in sortedDates) {
            val bounds = boundsByDate[date] ?: continue
            if (ts >= bounds.start && ts < bounds.end) return date
        }

        val calendarDate = ts.toLocalDateTime(timeZone).date
        if (calendarDate in boundsByDate) return calendarDate

        return sortedDates.minByOrNull { date ->
            val b = boundsByDate[date]!!
            minOf((ts - b.start).absoluteValue, (ts - b.end).absoluteValue)
        } ?: calendarDate
    }
}
