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

    /**
     * Rows this version can still finish. A row whose reason is `UNSUPPORTED_FORMAT` (the name of
     * `DiagnosticReason.UNSUPPORTED_FORMAT`) contains a record a newer version of LogDate wrote: it
     * stays in the inbox and [pending] offers it again, so an app update that reads it applies it,
     * but nothing this version does finishes it.
     */
    @Query(
        "SELECT COUNT(*) FROM sync_download_inbox WHERE ownerId = :owner AND serverOrigin = :origin " +
            "AND state != 'APPLIED' AND reason != 'UNSUPPORTED_FORMAT'",
    )
    suspend fun count(
        owner: String,
        origin: String,
    ): Int

    /** Every row of [type], applied or not. */
    @Query("SELECT * FROM sync_download_inbox WHERE ownerId = :owner AND serverOrigin = :origin AND entityType = :type")
    suspend fun all(
        owner: String,
        origin: String,
        type: String,
    ): List<DownloadInboxEntity>

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
