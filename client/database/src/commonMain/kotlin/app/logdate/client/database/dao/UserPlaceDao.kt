package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.logdate.client.database.entities.UserPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPlaceDao {
    
    @Query("SELECT * FROM user_places ORDER BY name ASC")
    suspend fun getAllPlaces(): List<UserPlaceEntity>
    
    @Query("SELECT * FROM user_places ORDER BY name ASC")
    fun observeAllPlaces(): Flow<List<UserPlaceEntity>>
    
    @Query("SELECT * FROM user_places WHERE id = :placeId")
    suspend fun getPlaceById(placeId: String): UserPlaceEntity?
    
    @Query("""
        SELECT * FROM user_places 
        WHERE (
            6371000 * acos(
                cos(radians(:latitude)) * cos(radians(latitude)) * 
                cos(radians(longitude) - radians(:longitude)) + 
                sin(radians(:latitude)) * sin(radians(latitude))
            )
        ) <= :radiusMeters
        ORDER BY (
            6371000 * acos(
                cos(radians(:latitude)) * cos(radians(latitude)) * 
                cos(radians(longitude) - radians(:longitude)) + 
                sin(radians(:latitude)) * sin(radians(latitude))
            )
        ) ASC
    """)
    suspend fun getPlacesNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): List<UserPlaceEntity>
    
    @Query("SELECT * FROM user_places WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchPlaces(query: String): List<UserPlaceEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: UserPlaceEntity)
    
    @Update
    suspend fun updatePlace(place: UserPlaceEntity)
    
    @Delete
    suspend fun deletePlace(place: UserPlaceEntity)
    
    @Query("DELETE FROM user_places WHERE id = :placeId")
    suspend fun deletePlaceById(placeId: String)
}