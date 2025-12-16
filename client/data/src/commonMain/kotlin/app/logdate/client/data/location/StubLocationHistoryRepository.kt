package app.logdate.client.data.location

import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.AltitudeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days

/**
 * Stub implementation of LocationHistoryRepository for testing and development.
 * Provides realistic mock location data.
 */
class StubLocationHistoryRepository : LocationHistoryRepository {
    
    private val mockLocationHistory = listOf(
        LocationHistoryItem(
            userId = "mock_user",
            deviceId = "mock_device",
            timestamp = Clock.System.now() - 2.hours,
            location = Location(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = LocationAltitude(52.0, AltitudeUnit.METERS)
            ),
            confidence = 0.9f,
            isGenuine = true
        ),
        LocationHistoryItem(
            userId = "mock_user",
            deviceId = "mock_device",
            timestamp = Clock.System.now() - 4.hours,
            location = Location(
                latitude = 37.7849,
                longitude = -122.4094,
                altitude = LocationAltitude(45.0, AltitudeUnit.METERS)
            ),
            confidence = 0.85f,
            isGenuine = true
        ),
        LocationHistoryItem(
            userId = "mock_user",
            deviceId = "mock_device",
            timestamp = Clock.System.now() - 1.days,
            location = Location(
                latitude = 37.7649,
                longitude = -122.4294,
                altitude = LocationAltitude(60.0, AltitudeUnit.METERS)
            ),
            confidence = 0.95f,
            isGenuine = true
        ),
        LocationHistoryItem(
            userId = "mock_user",
            deviceId = "mock_device",
            timestamp = Clock.System.now() - 2.days,
            location = Location(
                latitude = 37.7549,
                longitude = -122.4394,
                altitude = LocationAltitude(42.0, AltitudeUnit.METERS)
            ),
            confidence = 0.8f,
            isGenuine = true
        )
    )

    override suspend fun getAllLocationHistory(): List<LocationHistoryItem> {
        return mockLocationHistory.sortedByDescending { it.timestamp }
    }

    override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> {
        return flowOf(mockLocationHistory.sortedByDescending { it.timestamp })
    }

    override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> {
        return mockLocationHistory.sortedByDescending { it.timestamp }.take(limit)
    }

    override suspend fun getLocationHistoryBetween(
        startTime: Instant,
        endTime: Instant
    ): List<LocationHistoryItem> {
        return mockLocationHistory.filter { item ->
            item.timestamp >= startTime && item.timestamp <= endTime
        }.sortedByDescending { it.timestamp }
    }

    override suspend fun getLastLocation(): LocationHistoryItem? {
        return mockLocationHistory.maxByOrNull { it.timestamp }
    }

    override fun observeLastLocation(): Flow<LocationHistoryItem?> {
        return flowOf(mockLocationHistory.maxByOrNull { it.timestamp })
    }

    override suspend fun logLocation(
        location: Location,
        userId: String,
        deviceId: String,
        confidence: Float,
        isGenuine: Boolean
    ): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun deleteLocationEntry(
        userId: String,
        deviceId: String,
        timestamp: Instant
    ): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun deleteLocationsBetween(
        startTime: Instant,
        endTime: Instant
    ): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getLocationCount(): Int {
        return mockLocationHistory.size
    }
}