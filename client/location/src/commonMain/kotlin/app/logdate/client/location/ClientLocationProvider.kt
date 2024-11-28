package app.logdate.client.location

import app.logdate.shared.model.Location
import kotlinx.coroutines.flow.SharedFlow

/**
 * A provider that provides the current location of the client.
 *
 * It is expected that clients regularly update the location of the client whenever it changes.
 */
interface ClientLocationProvider {

    /**
     * The current location of the client.
     */
    val currentLocation: SharedFlow<Location>

    /**
     * Gets the current location of the client.
     */
    suspend fun getCurrentLocation(): Location

    /**
     * Forcibly updates the location of the client.
     */
    suspend fun refreshLocation()
}