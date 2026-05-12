package app.logdate.client.location.places

import app.logdate.shared.model.Location

/**
 * Unavailable [ExternalPlacesProvider] for platforms without a places API.
 *
 * Place suggestions are disabled explicitly on this platform.
 */
class UnavailableExternalPlacesProvider : ExternalPlacesProvider {
    override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> = emptyList()
}
