package app.logdate.client.data.places

import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.shared.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stub implementation of UserPlacesRepository for development purposes.
 * 
 * This implementation returns empty results and performs no-ops for all operations.
 * Should be replaced with a real implementation that stores user places in the database.
 */
class StubUserPlacesRepository : UserPlacesRepository {
    
    override suspend fun getAllPlaces(): List<Place> = emptyList()
    
    override fun observeAllPlaces(): Flow<List<Place>> = flowOf(emptyList())
    
    override suspend fun getPlacesNear(latitude: Double, longitude: Double, radiusMeters: Double): List<Place> = emptyList()
    
    override suspend fun getPlaceById(placeId: String): Place? = null
    
    override suspend fun createPlace(place: Place): Result<Place> = Result.success(place)
    
    override suspend fun updatePlace(place: Place): Result<Place> = Result.success(place)
    
    override suspend fun deletePlace(placeId: String): Result<Unit> = Result.success(Unit)
    
    override suspend fun searchPlaces(query: String): List<Place> = emptyList()
}