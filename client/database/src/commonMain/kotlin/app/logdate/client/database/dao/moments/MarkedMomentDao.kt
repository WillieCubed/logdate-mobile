package app.logdate.client.database.dao.moments

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface MarkedMomentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(moment: MarkedMomentEntity)

    @Query("SELECT * FROM marked_moments WHERE id = :id")
    suspend fun getById(id: Uuid): MarkedMomentEntity?

    @Query("SELECT * FROM marked_moments WHERE state = :state AND deletedAt IS NULL ORDER BY capturedAt DESC")
    fun observeByState(state: String): Flow<List<MarkedMomentEntity>>
}
