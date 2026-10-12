package app.logdate.client.database.dao.moments

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Dao
interface MarkedMomentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(moment: MarkedMomentEntity)

    @Query("SELECT * FROM marked_moments WHERE id = :id")
    suspend fun getById(id: Uuid): MarkedMomentEntity?

    @Query("SELECT * FROM marked_moments WHERE state = :state AND deletedAt IS NULL ORDER BY capturedAt DESC")
    fun observeByState(state: String): Flow<List<MarkedMomentEntity>>

    @Query("SELECT * FROM marked_moments WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<MarkedMomentEntity>>

    /** Records a place for a mark captured without one; 1 when it did, 0 when the mark has a place or was deleted. */
    @Query(
        "UPDATE marked_moments SET latitude = :latitude, longitude = :longitude, locationAccuracy = :accuracy, " +
            "lastUpdated = :lastUpdated WHERE id = :id AND latitude IS NULL AND deletedAt IS NULL",
    )
    suspend fun fillLocationIfMissing(
        id: Uuid,
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        lastUpdated: Instant,
    ): Int
}
