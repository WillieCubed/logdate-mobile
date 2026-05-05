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

    @Query("SELECT * FROM sync_cursors WHERE serverOrigin = :serverOrigin AND entityType = :entityType")
    suspend fun getCursor(
        serverOrigin: String,
        entityType: String,
    ): SyncCursorEntity?

    @Query("SELECT * FROM sync_cursors WHERE serverOrigin = '' AND entityType = :entityType")
    suspend fun getLegacyCursor(entityType: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursors")
    suspend fun deleteAllCursors()

    @Query("DELETE FROM sync_cursors WHERE serverOrigin = '' AND entityType = :entityType")
    suspend fun deleteLegacyCursor(entityType: String)

    // --- Pending Uploads ---

    @Query("SELECT * FROM pending_uploads WHERE serverOrigin = :serverOrigin AND entityType = :entityType ORDER BY createdAt ASC")
    suspend fun getPendingByType(
        serverOrigin: String,
        entityType: String,
    ): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads WHERE serverOrigin = '' AND entityType = :entityType ORDER BY createdAt ASC")
    suspend fun getLegacyPendingByType(entityType: String): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads WHERE serverOrigin = :serverOrigin AND entityType = :entityType AND entityId = :entityId")
    suspend fun getPending(
        serverOrigin: String,
        entityType: String,
        entityId: String,
    ): PendingUploadEntity?

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingUploadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPending(pending: PendingUploadEntity)

    @Query("DELETE FROM pending_uploads WHERE serverOrigin = :serverOrigin AND entityType = :entityType AND entityId = :entityId")
    suspend fun deletePending(
        serverOrigin: String,
        entityType: String,
        entityId: String,
    )

    @Query("DELETE FROM pending_uploads")
    suspend fun deleteAllPending()

    @Query("DELETE FROM pending_uploads WHERE serverOrigin = :serverOrigin")
    suspend fun deletePendingForOrigin(serverOrigin: String)

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE serverOrigin = :serverOrigin")
    suspend fun getPendingCount(serverOrigin: String): Int

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE serverOrigin = :serverOrigin")
    fun observePendingCount(serverOrigin: String): Flow<Int>

    @Query(
        "UPDATE pending_uploads SET retryCount = retryCount + 1 WHERE serverOrigin = :serverOrigin AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun incrementRetryCount(
        serverOrigin: String,
        entityType: String,
        entityId: String,
    )

    @Query("DELETE FROM pending_uploads WHERE serverOrigin = '' AND entityType = :entityType")
    suspend fun deleteLegacyPendingByType(entityType: String)
}
