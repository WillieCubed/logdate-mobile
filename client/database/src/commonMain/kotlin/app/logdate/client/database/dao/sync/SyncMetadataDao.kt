package app.logdate.client.database.dao.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for sync metadata operations.
 */
@Dao
interface SyncMetadataDao {
    // --- Sync Cursors ---

    @Query("SELECT * FROM sync_cursors WHERE entityType = :entityType")
    suspend fun getCursor(entityType: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursors")
    suspend fun deleteAllCursors()

    // --- Pending Uploads ---

    @Query("SELECT * FROM pending_uploads WHERE entityType = :entityType ORDER BY createdAt ASC")
    suspend fun getPendingByType(entityType: String): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingUploadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPending(pending: PendingUploadEntity)

    @Query("DELETE FROM pending_uploads WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deletePending(entityType: String, entityId: String)

    @Query("DELETE FROM pending_uploads")
    suspend fun deleteAllPending()

    @Query("SELECT COUNT(*) FROM pending_uploads")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM pending_uploads")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE pending_uploads SET retryCount = retryCount + 1 WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun incrementRetryCount(entityType: String, entityId: String)
}
