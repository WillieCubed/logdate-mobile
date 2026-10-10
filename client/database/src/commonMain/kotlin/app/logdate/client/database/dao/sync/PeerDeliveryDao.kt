package app.logdate.client.database.dao.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.sync.PeerDeliveryEntity

@Dao
interface PeerDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PeerDeliveryEntity)

    @Query(
        "SELECT * FROM peer_delivery WHERE peerScope = :peerScope AND direction = :direction " +
            "AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun get(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    ): PeerDeliveryEntity?

    @Query(
        "SELECT * FROM peer_delivery WHERE peerScope = :peerScope AND direction = :direction " +
            "AND nextAttemptAt IS NOT NULL AND nextAttemptAt <= :now ORDER BY nextAttemptAt ASC LIMIT :limit",
    )
    suspend fun due(
        peerScope: String,
        direction: String,
        now: Long,
        limit: Int,
    ): List<PeerDeliveryEntity>

    @Query(
        "DELETE FROM peer_delivery WHERE peerScope = :peerScope AND direction = :direction " +
            "AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun delete(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    )
}
