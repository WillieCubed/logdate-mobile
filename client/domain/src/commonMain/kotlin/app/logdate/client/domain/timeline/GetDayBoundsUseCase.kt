package app.logdate.client.domain.timeline

import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.model.DayBounds
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Use case to determine the semantic day bounds for a user.
 * 
 * A semantic day represents the user's actual waking day, which may not align with
 * calendar day boundaries (midnight to midnight). For example, if a user typically wakes
 * up at 7am and goes to sleep at 11pm, their "day" might be considered 7am to 11pm.
 */
class GetDayBoundsUseCase(
    private val healthRepository: LocalFirstHealthRepository
) {
    /**
     * Get the semantic day bounds for a specific date.
     * 
     * @param date The date to get bounds for
     * @param timeZone The user's current time zone
     * @return DayBounds containing start and end Instants for the semantic day
     */
    suspend operator fun invoke(date: LocalDate, timeZone: TimeZone): Result<DayBounds> {
        return try {
            val bounds = healthRepository.getDayBoundsForDate(date, timeZone)
            Result.success(bounds)
        } catch (e: Exception) {
            Napier.e("Error getting day bounds", e)
            Result.failure(e)
        }
    }
}