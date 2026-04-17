package app.logdate.client.database.dao.people

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.logdate.client.database.entities.people.InferredPersonClusterEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface InferredPersonClusterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cluster: InferredPersonClusterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clusters: List<InferredPersonClusterEntity>)

    @Update
    suspend fun update(cluster: InferredPersonClusterEntity)

    @Query("SELECT * FROM inferred_person_clusters WHERE id = :id LIMIT 1")
    suspend fun getById(id: Uuid): InferredPersonClusterEntity?

    @Query("SELECT * FROM inferred_person_clusters WHERE status = :status ORDER BY last_updated DESC")
    fun observeByStatus(status: String): Flow<List<InferredPersonClusterEntity>>

    @Query("SELECT * FROM inferred_person_clusters WHERE status = :status ORDER BY last_updated DESC")
    suspend fun getByStatus(status: String): List<InferredPersonClusterEntity>

    @Query("DELETE FROM inferred_person_clusters WHERE status = :status")
    suspend fun deleteByStatus(status: String)
}
