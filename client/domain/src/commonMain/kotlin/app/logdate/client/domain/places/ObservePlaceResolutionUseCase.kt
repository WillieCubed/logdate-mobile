package app.logdate.client.domain.places

import app.logdate.shared.model.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case for observing place resolution from a stream of locations.
 */
class ObservePlaceResolutionUseCase(
    private val resolveLocationToPlaceUseCase: ResolveLocationToPlaceUseCase
) {
    
    operator fun invoke(locations: Flow<Location>): Flow<PlaceResolutionResult> {
        return locations.map { location ->
            resolveLocationToPlaceUseCase(location)
        }
    }
}