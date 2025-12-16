package app.logdate.client.domain.location

import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe location history changes.
 */
class ObserveLocationHistoryUseCase(
    private val locationHistoryRepository: LocationHistoryRepository
) {
    
    operator fun invoke(): Flow<List<LocationHistoryItem>> {
        return locationHistoryRepository.observeLocationHistory()
    }
}