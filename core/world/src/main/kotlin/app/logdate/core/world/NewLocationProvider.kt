package app.logdate.core.world

import app.logdate.model.Location
import kotlinx.coroutines.flow.SharedFlow

/**
 * A provider that provides the current location of the client.
 *
 * It is expected that clients regularly update the location of the client whenever it changes.
 */
interface NewLocationProvider {

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