package app.logdate.client.domain.location

import app.logdate.client.repository.location.LocationHistoryRepository
import kotlin.time.Instant

/**
 * Deletes location history for a span of time.
 */
class DeleteLocationRangeUseCase(
    private val locationHistoryRepository: LocationHistoryRepository,
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
    ): Result<Unit> = locationHistoryRepository.deleteLocationsBetween(startTime, endTime)
}
