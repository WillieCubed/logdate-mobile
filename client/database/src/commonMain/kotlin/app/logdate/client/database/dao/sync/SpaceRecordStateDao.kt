package app.logdate.client.database.dao.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.sync.SpaceRecordStateEntity

/** The personal-space records this device knows, scoped by account and server. */
@Dao
interface SpaceRecordStateDao {
    @Query(
        "SELECT * FROM sync_space_records WHERE ownerId = :owner AND serverOrigin = :origin " +
            "AND collection = :collection AND innerRkey = :innerRkey",
    )
    suspend fun get(
        owner: String,
        origin: String,
        collection: String,
        innerRkey: String,
    ): SpaceRecordStateEntity?

    @Query("SELECT * FROM sync_space_records WHERE ownerId = :owner AND serverOrigin = :origin AND sealedRkey = :sealedRkey")
    suspend fun bySealedRkey(
        owner: String,
        origin: String,
        sealedRkey: String,
    ): SpaceRecordStateEntity?

    @Query("SELECT * FROM sync_space_records WHERE ownerId = :owner AND serverOrigin = :origin")
    suspend fun all(
        owner: String,
        origin: String,
    ): List<SpaceRecordStateEntity>

    /** Replaces the row for the record, and any other row that held the same sealed key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SpaceRecordStateEntity)

    @Query(
        "DELETE FROM sync_space_records WHERE ownerId = :owner AND serverOrigin = :origin " +
            "AND collection = :collection AND innerRkey = :innerRkey",
    )
    suspend fun delete(
        owner: String,
        origin: String,
        collection: String,
        innerRkey: String,
    )
}
