package app.logdate.client.repository.places

import app.logdate.shared.model.Place
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing user-defined places.
 * 
 * Handles storage and retrieval of semantic places that users have created
 * or learned from their location patterns.
 */
interface UserPlacesRepository {
    
    /**
     * Gets all user-defined places.
     */
    suspend fun getAllPlaces(): List<Place>
    
    /**
     * Observes all user-defined places.
     */
    fun observeAllPlaces(): Flow<List<Place>>
    
    /**
     * Gets places within a specified radius of coordinates.
     */
    suspend fun getPlacesNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): List<Place>
    
    /**
     * Gets a specific place by ID.
     */
    suspend fun getPlaceById(placeId: String): Place?
    
    /**
     * Creates a new user-defined place.
     */
    suspend fun createPlace(place: Place): Result<Place>
    
    /**
     * Updates an existing place.
     */
    suspend fun updatePlace(place: Place): Result<Place>
    
    /**
     * Deletes a place.
     */
    suspend fun deletePlace(placeId: String): Result<Unit>
    
    /**
     * Searches places by name.
     */
    suspend fun searchPlaces(query: String): List<Place>
}