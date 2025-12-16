package app.logdate.client.domain.location

import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository

/**
 * Use case to get location history.
 */
class GetLocationHistoryUseCase(
    private val locationHistoryRepository: LocationHistoryRepository
) {
    
    suspend operator fun invoke(limit: Int = 50): List<LocationHistoryItem> {
        return locationHistoryRepository.getRecentLocationHistory(limit)
    }
}