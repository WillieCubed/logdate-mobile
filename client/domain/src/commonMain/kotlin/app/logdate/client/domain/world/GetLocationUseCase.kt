package app.logdate.client.domain.world

import app.logdate.client.location.ClientLocationProvider
import app.logdate.shared.model.Location

/**
 * A use case that retrieves the user's current location.
 *
 * This does not observe the user's location, it only retrieves the current location. To observe the
 * user's location, use [ObserveLocationUseCase].
 *
 * @see ObserveLocationUseCase
 */
class GetLocationUseCase(
    private val locationProvider: ClientLocationProvider,
) {
    suspend operator fun invoke(): Location {
        return locationProvider.getCurrentLocation()
    }
}