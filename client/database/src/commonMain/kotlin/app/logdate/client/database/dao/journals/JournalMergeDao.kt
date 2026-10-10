package app.logdate.client.database.dao.journals

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.journals.JournalMergeEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface JournalMergeDao {
    @Query("SELECT * FROM journal_merges WHERE ownerId = :ownerId AND serverOrigin = :origin AND sourceId = :sourceId")
    suspend fun find(
        ownerId: String,
        origin: String,
        sourceId: Uuid,
    ): JournalMergeEntity?

    @Query("SELECT * FROM journal_merges WHERE ownerId = :ownerId AND serverOrigin = :origin ORDER BY createdAt, sourceId")
    suspend fun all(
        ownerId: String,
        origin: String,
    ): List<JournalMergeEntity>

    @Query("SELECT * FROM journal_merges WHERE ownerId = :ownerId AND serverOrigin = :origin ORDER BY createdAt, sourceId")
    fun observe(
        ownerId: String,
        origin: String,
    ): Flow<List<JournalMergeEntity>>

    @Query("SELECT * FROM journal_merges ORDER BY createdAt, sourceId")
    fun observeAll(): Flow<List<JournalMergeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(merge: JournalMergeEntity)
}
