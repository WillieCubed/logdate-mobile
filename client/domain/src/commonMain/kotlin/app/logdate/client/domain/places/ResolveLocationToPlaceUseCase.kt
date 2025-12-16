package app.logdate.client.domain.places

import app.logdate.client.location.places.ExternalPlacesProvider
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.shared.model.Location
import app.logdate.shared.model.Place
import kotlin.math.*

/**
 * Domain use case that resolves a raw location into a semantic place.
 * 
 * Prioritizes user-defined places over external place suggestions.
 */
class ResolveLocationToPlaceUseCase(
    private val userPlacesRepository: UserPlacesRepository,
    private val externalPlacesProvider: ExternalPlacesProvider,
    private val placeMatchingRadius: Double = 100.0 // meters
) {
    
    suspend operator fun invoke(location: Location): PlaceResolutionResult {
        // First, check user-defined places
        val userPlace = findMatchingUserPlace(location)
        if (userPlace != null) {
            return PlaceResolutionResult.UserDefinedPlace(userPlace)
        }
        
        // Fallback to external places
        val externalSuggestions = try {
            externalPlacesProvider.searchNearbyPlaces(location)
        } catch (e: Exception) {
            emptyList()
        }
        
        val bestSuggestion = externalSuggestions.firstOrNull()
        return if (bestSuggestion != null) {
            PlaceResolutionResult.ExternalSuggestion(bestSuggestion)
        } else {
            PlaceResolutionResult.UnknownLocation(location)
        }
    }
    
    private suspend fun findMatchingUserPlace(location: Location): Place? {
        val nearbyUserPlaces = userPlacesRepository.getPlacesNear(
            latitude = location.latitude,
            longitude = location.longitude,
            radiusMeters = placeMatchingRadius
        )
        
        return nearbyUserPlaces.minByOrNull { userPlace ->
            calculateDistance(
                location.latitude, location.longitude,
                userPlace.latitude, userPlace.longitude
            )
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * PI / 180) * cos(lat2 * PI / 180) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}

/**
 * Result of place resolution with different types based on data source.
 */
sealed class PlaceResolutionResult {
    data class UserDefinedPlace(val place: Place) : PlaceResolutionResult()
    data class ExternalSuggestion(val suggestion: app.logdate.client.location.places.PlaceSuggestion) : PlaceResolutionResult()
    data class UnknownLocation(val location: Location) : PlaceResolutionResult()
}