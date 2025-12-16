package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.logdate.client.database.entities.LocationLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface LocationHistoryDao {
    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLocation(): LocationLogEntity?

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC LIMIT 1")
    fun observeLastLocation(): Flow<LocationLogEntity?>

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC")
    suspend fun getAllLocationHistory(): List<LocationLogEntity>

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC")
    fun observeAllLocationHistory(): Flow<List<LocationLogEntity>>

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLocationHistory(limit: Int): List<LocationLogEntity>

    @Query("SELECT * FROM location_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getLocationHistoryBetween(startTime: Instant, endTime: Instant): List<LocationLogEntity>

    @Query("SELECT * FROM location_logs WHERE user_id = :userId AND device_id = :deviceId AND timestamp = :timestamp")
    suspend fun getLocationById(userId: String, deviceId: String, timestamp: Instant): LocationLogEntity?

    @Insert
    suspend fun addLocationLog(locationLog: LocationLogEntity)

    @Update
    suspend fun editLocationLog(locationLog: LocationLogEntity)

    @Delete
    suspend fun deleteLog(locationLog: LocationLogEntity)

    @Query("DELETE FROM location_logs WHERE user_id = :userId AND device_id = :deviceId AND timestamp = :timestamp")
    suspend fun deleteLocationById(userId: String, deviceId: String, timestamp: Instant)

    @Query("DELETE FROM location_logs WHERE timestamp >= :start AND timestamp < :end")
    suspend fun deleteLogsWithinRange(start: Instant, end: Instant)

    @Query("DELETE FROM location_logs WHERE device_id = :deviceId")
    suspend fun deleteLogsByDevice(deviceId: String)

    @Query("SELECT COUNT(*) FROM location_logs")
    suspend fun getLocationCount(): Int
}