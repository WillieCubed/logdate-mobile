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
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Offline-first implementation of location history repository.
 */
class OfflineFirstLocationHistoryRepository(
    private val locationHistoryDao: LocationHistoryDao,
) : LocationHistoryRepository {
    override suspend fun getAllLocationHistory(): List<LocationHistoryItem> =
        locationHistoryDao.getAllLocationHistory().map {
            it.toDomainModel()
        }

    override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> =
        locationHistoryDao.observeAllLocationHistory().map { entities ->
            entities.map { it.toDomainModel() }
        }

    override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> =
        locationHistoryDao.getRecentLocationHistory(limit).map {
            it.toDomainModel()
        }

    override suspend fun getLocationHistoryBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<LocationHistoryItem> =
        locationHistoryDao
            .getLocationHistoryBetween(startTime, endTime)
            .map { it.toDomainModel() }

    override suspend fun getLastLocation(): LocationHistoryItem? = locationHistoryDao.getLastLocation()?.toDomainModel()

    override fun observeLastLocation(): Flow<LocationHistoryItem?> = locationHistoryDao.observeLastLocation().map { it?.toDomainModel() }

    override suspend fun logLocation(
        location: Location,
        userId: String,
        deviceId: String,
        confidence: Float,
        isGenuine: Boolean,
    ): Result<Unit> =
        try {
            val entity =
                LocationLogEntity(
                    userId = userId,
                    deviceId = deviceId,
                    timestamp = Clock.System.now(),
                    location =
                        Coordinates(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = location.altitude.value,
                        ),
                    confidence = confidence,
                    isGenuine = isGenuine,
                )
            locationHistoryDao.addLocationLog(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override suspend fun deleteLocationEntry(
        userId: String,
        deviceId: String,
        timestamp: Instant,
    ): Result<Unit> =
        try {
            locationHistoryDao.deleteLocationById(userId, deviceId, timestamp)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override suspend fun deleteLocationsBetween(
        startTime: Instant,
        endTime: Instant,
    ): Result<Unit> =
        try {
            locationHistoryDao.deleteLogsWithinRange(startTime, endTime)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override suspend fun getLocationCount(): Int = locationHistoryDao.getLocationCount()

    private fun LocationLogEntity.toDomainModel(): LocationHistoryItem =
        LocationHistoryItem(
            userId = userId,
            deviceId = deviceId,
            timestamp = timestamp,
            location =
                Location(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude =
                        LocationAltitude(
                            value = location.altitude,
                            units = AltitudeUnit.METERS,
                        ),
                ),
            confidence = confidence,
            isGenuine = isGenuine,
        )
}
