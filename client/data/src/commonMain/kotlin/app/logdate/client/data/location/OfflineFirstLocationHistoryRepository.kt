package app.logdate.client.data.location

import app.logdate.client.database.dao.LocationHistoryDao
import app.logdate.client.database.entities.Coordinates
import app.logdate.client.database.entities.LocationLogEntity
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        logLocation(
            LocationLogRecord(
                userId = userId,
                deviceId = deviceId,
                timestamp =
                    Instant.fromEpochMilliseconds(
                        kotlin.time.Clock.System
                            .now()
                            .toEpochMilliseconds(),
                    ),
                loggedAt =
                    kotlin.time.Clock.System
                        .now(),
                location = location,
                confidence = confidence,
                isGenuine = isGenuine,
            ),
        )

    override suspend fun logLocation(record: LocationLogRecord): Result<Unit> =
        try {
            val entity =
                LocationLogEntity(
                    sampleId = record.sampleId,
                    userId = record.userId,
                    deviceId = record.deviceId,
                    timestamp = record.timestamp,
                    loggedAt = record.loggedAt,
                    location =
                        Coordinates(
                            latitude = record.location.latitude,
                            longitude = record.location.longitude,
                            altitude = record.location.altitude.value,
                        ),
                    confidence = record.confidence,
                    isGenuine = record.isGenuine,
                    capturePipeline = record.capturePipeline.name,
                    captureSource = record.captureSource.name,
                    accuracyMeters = record.accuracyMeters,
                    speedMetersPerSecond = record.speedMetersPerSecond,
                    bearingDegrees = record.bearingDegrees,
                    isMock = record.isMock,
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
            sampleId = sampleId,
            userId = userId,
            deviceId = deviceId,
            timestamp = timestamp,
            loggedAt = loggedAt,
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
            capturePipeline = capturePipeline.toCapturePipeline(),
            captureSource = captureSource.toCaptureSource(),
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
            isMock = isMock,
        )

    private fun String.toCapturePipeline(): LocationCapturePipeline =
        runCatching {
            LocationCapturePipeline.valueOf(this)
        }.getOrElse {
            Napier.w("Unknown location capture pipeline '$this'; falling back to LEGACY")
            LocationCapturePipeline.LEGACY
        }

    private fun String.toCaptureSource(): LocationCaptureSource =
        runCatching {
            LocationCaptureSource.valueOf(this)
        }.getOrElse {
            Napier.w("Unknown location capture source '$this'; falling back to BACKGROUND_PERIODIC")
            LocationCaptureSource.BACKGROUND_PERIODIC
        }
}
