package app.logdate.client.location

import app.logdate.shared.model.Location
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Clock

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
     * Returns true if the app has been granted location permission by the user.
     */
    fun hasLocationPermission(): Boolean

    /**
     * Gets the current location of the client.
     */
    suspend fun getCurrentLocation(): Location

    /**
     * Gets the current location along with how precise it is and when it was taken. Location
     * history needs both to tell a real move from measurement noise. Providers that only know a
     * position report it as taken now, with unknown accuracy.
     */
    suspend fun getCurrentFix(): LocationFix = LocationFix(getCurrentLocation(), Clock.System.now())

    /**
     * Forcibly updates the location of the client.
     */
    suspend fun refreshLocation()
}
