package app.logdate.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.logdate.core.database.model.LocationLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface LocationHistoryDao {
    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLocation(): LocationLogEntity

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC LIMIT 1")
    fun observeLastLocation(): Flow<LocationLogEntity>

    @Insert
    suspend fun addLocationLog(locationLog: LocationLogEntity)

    @Update
    suspend fun editLocationLog(locationLog: LocationLogEntity)

    @Delete
    suspend fun deleteLog(locationLog: LocationLogEntity)

    @Query("DELETE FROM location_logs WHERE timestamp >= :start AND timestamp < :end")
    suspend fun deleteLogsWithinRange(start: Instant, end: Instant)

    @Query("DELETE FROM location_logs WHERE device_id = :deviceId")
    suspend fun deleteLogsByDevice(deviceId: String)
}