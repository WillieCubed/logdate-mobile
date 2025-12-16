package app.logdate.client.location.places

import app.logdate.shared.model.Location

/**
 * Stub implementation of ExternalPlacesProvider for development purposes.
 * 
 * This implementation returns empty results for all place searches.
 * Should be replaced with a real implementation using Google Places API or similar.
 */
class StubExternalPlacesProvider : ExternalPlacesProvider {
    
    override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> = emptyList()
}