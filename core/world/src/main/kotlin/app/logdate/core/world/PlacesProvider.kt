package app.logdate.core.world

import app.logdate.model.UserPlace
import kotlinx.coroutines.flow.Flow

interface PlacesProvider {
    fun observeCurrentPlace(): Flow<UserPlace>

    /**
     * Manually refreshes the current place.
     *
     * Note that a [PlacesProvider] may automatically refresh the current place.
     */
    suspend fun refreshCurrentPlace()

    /**
     * Returns a list of places that match the given query.
     *
     * Ranked in decreasing order of confidence.
     */
    fun resolvePlace(latitude: Double, longitude: Double): List<UserPlaceResult>

    /**
     * Returns a list of places that are near the given place.
     *
     * Ranked in decreasing order of confidence.
     */
    fun getNearbyPlaces(place: UserPlace): List<UserPlaceResult>

    /**
     * Returns a list of places near the given coordinates.
     *
     * Ranked in decreasing order of confidence.
     */
    fun getNearbyPlaces(latitude: Double, longitude: Double): List<UserPlaceResult>
}
