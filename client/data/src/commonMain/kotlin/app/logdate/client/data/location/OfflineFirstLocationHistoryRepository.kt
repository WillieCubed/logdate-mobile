package app.logdate.client.data.location

import app.logdate.client.database.dao.LocationHistoryDao
import app.logdate.client.database.entities.Coordinates
import app.logdate.client.database.entities.LocationLogEntity
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * Offline-first implementation of location history repository.
 */
class OfflineFirstLocationHistoryRepository(
    private val locationHistoryDao: LocationHistoryDao
) : LocationHistoryRepository {
    
    override suspend fun getAllLocationHistory(): List<LocationHistoryItem> {
        return locationHistoryDao.getAllLocationHistory().map { it.toDomainModel() }
    }
    
    override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> {
        return locationHistoryDao.observeAllLocationHistory().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> {
        return locationHistoryDao.getRecentLocationHistory(limit).map { it.toDomainModel() }
    }
    
    override suspend fun getLocationHistoryBetween(
        startTime: Instant,
        endTime: Instant
    ): List<LocationHistoryItem> {
        return locationHistoryDao.getLocationHistoryBetween(startTime, endTime)
            .map { it.toDomainModel() }
    }
    
    override suspend fun getLastLocation(): LocationHistoryItem? {
        return locationHistoryDao.getLastLocation()?.toDomainModel()
    }
    
    override fun observeLastLocation(): Flow<LocationHistoryItem?> {
        return locationHistoryDao.observeLastLocation().map { it?.toDomainModel() }
    }
    
    override suspend fun logLocation(
        location: Location,
        userId: String,
        deviceId: String,
        confidence: Float,
        isGenuine: Boolean
    ): Result<Unit> {
        return try {
            val entity = LocationLogEntity(
                userId = userId,
                deviceId = deviceId,
                timestamp = kotlinx.datetime.Clock.System.now(),
                location = Coordinates(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude.value
                ),
                confidence = confidence,
                isGenuine = isGenuine
            )
            locationHistoryDao.addLocationLog(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteLocationEntry(
        userId: String,
        deviceId: String,
        timestamp: Instant
    ): Result<Unit> {
        return try {
            locationHistoryDao.deleteLocationById(userId, deviceId, timestamp)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteLocationsBetween(
        startTime: Instant,
        endTime: Instant
    ): Result<Unit> {
        return try {
            locationHistoryDao.deleteLogsWithinRange(startTime, endTime)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getLocationCount(): Int {
        return locationHistoryDao.getLocationCount()
    }
    
    private fun LocationLogEntity.toDomainModel(): LocationHistoryItem {
        return LocationHistoryItem(
            userId = userId,
            deviceId = deviceId,
            timestamp = timestamp,
            location = Location(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = LocationAltitude(
                    value = location.altitude,
                    units = AltitudeUnit.METERS
                )
            ),
            confidence = confidence,
            isGenuine = isGenuine
        )
    }
}