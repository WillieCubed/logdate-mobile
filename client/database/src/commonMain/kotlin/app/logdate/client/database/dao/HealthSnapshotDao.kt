package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.HealthSnapshotEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface HealthSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: HealthSnapshotEntity)

    @Query("SELECT * FROM health_snapshots WHERE note_id = :noteId")
    suspend fun getByNoteId(noteId: Uuid): HealthSnapshotEntity?

    @Query("SELECT * FROM health_snapshots WHERE note_id = :noteId")
    fun observeByNoteId(noteId: Uuid): Flow<HealthSnapshotEntity?>

    @Query("SELECT * FROM health_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): HealthSnapshotEntity?

    @Query("SELECT * FROM health_snapshots ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<HealthSnapshotEntity>>

    @Query("SELECT * FROM health_snapshots WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun observeInRange(
        start: Long,
        end: Long,
    ): Flow<List<HealthSnapshotEntity>>

    @Query("DELETE FROM health_snapshots WHERE id = :id")
    suspend fun delete(id: Uuid)

    @Query("DELETE FROM health_snapshots WHERE note_id = :noteId")
    suspend fun deleteByNoteId(noteId: Uuid)

    @Query("SELECT * FROM health_snapshots WHERE timestamp > :since ORDER BY timestamp ASC")
    suspend fun getAfter(since: Long): List<HealthSnapshotEntity>
}
