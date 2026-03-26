package app.logdate.client.domain.timeline

import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.model.DayBounds
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Resolves the start and end of a user's "day" for a given calendar date.
 *
 * Reads the user's day-boundary preference and passes it to the health
 * repository. Consumers grouping multiple notes should prefer
 * [GroupNotesByDayBoundsUseCase], which handles cross-day assignment.
 */
class GetDayBoundsUseCase(
    private val healthRepository: LocalFirstHealthRepository,
    private val dayBoundarySettingsRepository: DayBoundarySettingsRepository,
) {
    suspend operator fun invoke(
        date: LocalDate,
        timeZone: TimeZone,
    ): Result<DayBounds> =
        try {
            val settings = dayBoundarySettingsRepository.getSettings()
            val bounds =
                healthRepository.getDayBoundsForDate(
                    date = date,
                    timeZone = timeZone,
                    sleepBasedBoundariesEnabled = settings.sleepBasedBoundariesEnabled,
                )
            Result.success(bounds)
        } catch (e: Exception) {
            Napier.e("Error getting day bounds", e)
            Result.failure(e)
        }
}
