package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.logdate.client.database.entities.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Update
    suspend fun update(event: EventEntity)

    @Query("SELECT * FROM events WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: Uuid): EventEntity?

    @Query("SELECT * FROM events WHERE id IN (:ids) AND deleted_at IS NULL")
    suspend fun getByIds(ids: List<Uuid>): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: Uuid): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE deleted_at IS NULL ORDER BY start_time DESC")
    fun observeAll(): Flow<List<EventEntity>>

    /**
     * Observe events that overlap the given time window. An event overlaps if its [start_time]
     * is before [end] and its [end_time] (or [start_time], for point-in-time events) is at or
     * after [start].
     */
    @Query(
        """
        SELECT * FROM events
        WHERE deleted_at IS NULL
          AND start_time < :end
          AND COALESCE(end_time, start_time) >= :start
        ORDER BY start_time ASC
        """,
    )
    fun observeForDateRange(
        start: Instant,
        end: Instant,
    ): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE external_calendar_id = :externalId AND deleted_at IS NULL")
    suspend fun getByExternalCalendarId(externalId: String): EventEntity?

    @Query("UPDATE events SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(
        id: Uuid,
        deletedAt: Long,
    )

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun hardDelete(id: Uuid)
}
