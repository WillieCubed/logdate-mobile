package app.logdate.client.database.dao.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.sync.DownloadCheckpointEntity
import app.logdate.client.database.entities.sync.DownloadInboxEntity

@Dao
interface DownloadInboxDao {
    @Query("SELECT * FROM sync_download_inbox WHERE ownerId = :owner AND serverOrigin = :origin AND entityType = :type AND entityId = :id")
    suspend fun get(
        owner: String,
        origin: String,
        type: String,
        id: String,
    ): DownloadInboxEntity?

    @Query(
        "SELECT * FROM sync_download_inbox WHERE ownerId = :owner AND serverOrigin = :origin AND entityType = :type AND state != 'APPLIED' AND nextAttemptAt <= :now ORDER BY nextAttemptAt, version LIMIT 100",
    )
    suspend fun pending(
        owner: String,
        origin: String,
        type: String,
        now: Long,
    ): List<DownloadInboxEntity>

    @Query("SELECT COUNT(*) FROM sync_download_inbox WHERE ownerId = :owner AND serverOrigin = :origin AND state != 'APPLIED'")
    suspend fun count(
        owner: String,
        origin: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: DownloadInboxEntity)

    @Query("SELECT * FROM sync_download_checkpoints WHERE ownerId = :owner AND serverOrigin = :origin AND entityType = :type")
    suspend fun checkpoint(
        owner: String,
        origin: String,
        type: String,
    ): DownloadCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun checkpoint(row: DownloadCheckpointEntity)

    @Query("UPDATE sync_download_inbox SET nextAttemptAt = 0 WHERE ownerId = :owner AND serverOrigin = :origin AND state != 'APPLIED'")
    suspend fun release(
        owner: String,
        origin: String,
    )
}
