package app.logdate.core.database.dao.rewind

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A data access object for LogDate Rewinds.
 *
 * Generally, it is expected that Rewinds are generated on demand or as dynamically as possible, but
 * this DAO is provided for the purpose of caching Rewinds for offline use.
 */
@OptIn(ExperimentalUuidApi::class)
@Dao
interface CachedRewindDao {
    /**
     * Get the cached Rewind by its unique identifier.
     */
    @OptIn(ExperimentalUuidApi::class)
    @Query("SELECT * FROM rewinds WHERE uid = :uid")
    // TODO: Join with other tables to get the full Rewind data
    suspend fun getRewind(uid: Uuid): RewindEntity

    /**
     * Insert a new Rewind into the cache.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRewind(rewind: RewindEntity)

    @Query("DELETE FROM rewinds WHERE uid = :uid")
    suspend fun deleteRewind(uid: Uuid)
}

@OptIn(ExperimentalUuidApi::class)
@Entity(
    tableName = "rewinds",
)
data class RewindEntity(
    @PrimaryKey
    val uid: Uuid,
)