package app.logdate.client.domain.world

import app.logdate.client.location.ClientLocationProvider
import app.logdate.shared.model.Location
import kotlinx.coroutines.flow.Flow

/**
 * A use case for observing the user's current location.
 *
 * This use case will observe the user's location and emit updates as the user moves.
 *
 * @see GetLocationUseCase
 */
class ObserveLocationUseCase(
    private val locationProvider: ClientLocationProvider,
) {
    operator fun invoke(): Flow<Location> {
        return locationProvider.currentLocation
    }
}