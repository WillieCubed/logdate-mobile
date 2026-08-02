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

    @Query("SELECT * FROM sync_cursors WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin AND entityType = :entityType")
    suspend fun getCursor(
        ownerId: String,
        serverOrigin: String,
        entityType: String,
    ): SyncCursorEntity?

    @Query("SELECT * FROM sync_cursors WHERE ownerId = '' AND serverOrigin = :serverOrigin AND entityType = :entityType")
    suspend fun getLegacyCursor(
        serverOrigin: String,
        entityType: String,
    ): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursors WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin")
    suspend fun deleteCursorsForOrigin(
        ownerId: String,
        serverOrigin: String,
    )

    @Query("DELETE FROM sync_cursors WHERE ownerId = '' AND serverOrigin = :serverOrigin AND entityType = :entityType")
    suspend fun deleteLegacyCursor(
        serverOrigin: String,
        entityType: String,
    )

    // --- Pending Uploads ---

    @Query(
        "SELECT * FROM pending_uploads WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin AND entityType = :entityType ORDER BY createdAt ASC",
    )
    suspend fun getPendingByType(
        ownerId: String,
        serverOrigin: String,
        entityType: String,
    ): List<PendingUploadEntity>

    @Query(
        "SELECT * FROM pending_uploads WHERE ownerId = '' AND serverOrigin = :serverOrigin AND entityType = :entityType ORDER BY createdAt ASC",
    )
    suspend fun getLegacyPendingByType(
        serverOrigin: String,
        entityType: String,
    ): List<PendingUploadEntity>

    @Query(
        "SELECT * FROM pending_uploads WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun getPending(
        ownerId: String,
        serverOrigin: String,
        entityType: String,
        entityId: String,
    ): PendingUploadEntity?

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingUploadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPending(pending: PendingUploadEntity)

    @Query(
        "DELETE FROM pending_uploads WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun deletePending(
        ownerId: String,
        serverOrigin: String,
        entityType: String,
        entityId: String,
    )

    @Query("DELETE FROM pending_uploads WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin")
    suspend fun deletePendingForOrigin(
        ownerId: String,
        serverOrigin: String,
    )

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin")
    suspend fun getPendingCount(
        ownerId: String,
        serverOrigin: String,
    ): Int

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin")
    fun observePendingCount(
        ownerId: String,
        serverOrigin: String,
    ): Flow<Int>

    @Query(
        "UPDATE pending_uploads SET retryCount = retryCount + 1 WHERE ownerId = :ownerId AND serverOrigin = :serverOrigin AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun incrementRetryCount(
        ownerId: String,
        serverOrigin: String,
        entityType: String,
        entityId: String,
    )

    @Query("DELETE FROM pending_uploads WHERE ownerId = '' AND serverOrigin = :serverOrigin AND entityType = :entityType")
    suspend fun deleteLegacyPendingByType(
        serverOrigin: String,
        entityType: String,
    )
}
