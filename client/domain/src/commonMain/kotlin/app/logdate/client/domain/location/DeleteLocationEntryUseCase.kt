package app.logdate.client.domain.location

import app.logdate.client.repository.location.LocationHistoryRepository
import kotlinx.datetime.Instant

/**
 * Use case to delete a specific location entry.
 */
class DeleteLocationEntryUseCase(
    private val locationHistoryRepository: LocationHistoryRepository
) {
    
    suspend operator fun invoke(
        userId: String,
        deviceId: String,
        timestamp: Instant
    ): Result<Unit> {
        return locationHistoryRepository.deleteLocationEntry(userId, deviceId, timestamp)
    }
}