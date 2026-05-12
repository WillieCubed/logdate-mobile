package app.logdate.client.e2e

import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class InMemoryLocationHistoryRepository : LocationHistoryRepository {
    private val locationHistory =
        listOf(
            locationHistoryItem(
                timestamp = Clock.System.now() - 2.hours,
                latitude = 37.7749,
                longitude = -122.4194,
                altitudeMeters = 52.0,
                confidence = 0.9f,
            ),
            locationHistoryItem(
                timestamp = Clock.System.now() - 4.hours,
                latitude = 37.7849,
                longitude = -122.4094,
                altitudeMeters = 45.0,
                confidence = 0.85f,
            ),
            locationHistoryItem(
                timestamp = Clock.System.now() - 1.days,
                latitude = 37.7649,
                longitude = -122.4294,
                altitudeMeters = 60.0,
                confidence = 0.95f,
            ),
            locationHistoryItem(
                timestamp = Clock.System.now() - 2.days,
                latitude = 37.7549,
                longitude = -122.4394,
                altitudeMeters = 42.0,
                confidence = 0.8f,
            ),
        )

    override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = sortedLocationHistory()

    override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = flowOf(sortedLocationHistory())

    override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = sortedLocationHistory().take(limit)

    override suspend fun getLocationHistoryBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<LocationHistoryItem> =
        locationHistory
            .filter { item -> item.timestamp >= startTime && item.timestamp <= endTime }
            .sortedByDescending { it.timestamp }

    override suspend fun getLastLocation(): LocationHistoryItem? = locationHistory.maxByOrNull { it.timestamp }

    override fun observeLastLocation(): Flow<LocationHistoryItem?> = flowOf(locationHistory.maxByOrNull { it.timestamp })

    override suspend fun logLocation(
        location: Location,
        userId: String,
        deviceId: String,
        confidence: Float,
        isGenuine: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteLocationEntry(
        userId: String,
        deviceId: String,
        timestamp: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteLocationsBetween(
        startTime: Instant,
        endTime: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getLocationCount(): Int = locationHistory.size

    private fun sortedLocationHistory(): List<LocationHistoryItem> = locationHistory.sortedByDescending { it.timestamp }

    private fun locationHistoryItem(
        timestamp: Instant,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double,
        confidence: Float,
    ): LocationHistoryItem =
        LocationHistoryItem(
            userId = "e2e_user",
            deviceId = "e2e_device",
            timestamp = timestamp,
            location =
                Location(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = LocationAltitude(altitudeMeters, AltitudeUnit.METERS),
                ),
            confidence = confidence,
            isGenuine = true,
        )
}
