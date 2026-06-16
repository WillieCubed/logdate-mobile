package app.logdate.client.domain.location

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

/**
 * Tests for [LocationRetryWorker].
 *
 * Verifies that the worker correctly handles retrying location log operations,
 * ensuring that the original observed metadata is preserved while updating the
 * logging timestamp.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationRetryWorkerTest {
    @Test
    fun `retry preserves observed payload and only refreshes logged time`() =
        runTest {
            val repository = RecordingLocationHistoryRepository()
            val retryWorker =
                LocationRetryWorker(
                    locationProvider = FakeLocationProvider(),
                    locationHistoryRepository = repository,
                    coroutineScope = CoroutineScope(backgroundScope.coroutineContext),
                )
            val observedAt = Instant.fromEpochMilliseconds(1_234_567L)
            val originalLoggedAt = Instant.fromEpochMilliseconds(1_235_000L)
            val record =
                LocationLogRecord(
                    sampleId = "sample-123",
                    userId = "user",
                    deviceId = "device",
                    timestamp = observedAt,
                    loggedAt = originalLoggedAt,
                    location =
                        Location(
                            latitude = 37.7749,
                            longitude = -122.4194,
                            altitude = LocationAltitude(15.0, AltitudeUnit.METERS),
                        ),
                    confidence = 0.8f,
                    isGenuine = true,
                    capturePipeline = LocationCapturePipeline.OPTIMIZED_BACKGROUND,
                    captureSource = LocationCaptureSource.PASSIVE_UPDATE,
                    accuracyMeters = 12f,
                    speedMetersPerSecond = 1.5f,
                    bearingDegrees = 180f,
                    isMock = false,
                )

            retryWorker.scheduleRetry(record)
            advanceTimeBy(2_100)

            val retriedRecord = repository.lastRecord
            assertNotNull(retriedRecord)
            assertEquals(record.sampleId, retriedRecord.sampleId)
            assertEquals(record.timestamp, retriedRecord.timestamp)
            assertEquals(record.location, retriedRecord.location)
            assertEquals(record.capturePipeline, retriedRecord.capturePipeline)
            assertEquals(record.captureSource, retriedRecord.captureSource)
            assertEquals(record.accuracyMeters, retriedRecord.accuracyMeters)
            assertEquals(record.speedMetersPerSecond, retriedRecord.speedMetersPerSecond)
            assertEquals(record.bearingDegrees, retriedRecord.bearingDegrees)
            assertEquals(record.isMock, retriedRecord.isMock)
            assertEquals(record.confidence, retriedRecord.confidence)
            assertEquals(record.isGenuine, retriedRecord.isGenuine)
            assertNotEquals(record.loggedAt, retriedRecord.loggedAt)
        }
}

/**
 * A fake [ClientLocationProvider] for testing.
 */
private class FakeLocationProvider : ClientLocationProvider {
    override val currentLocation = kotlinx.coroutines.flow.MutableSharedFlow<Location>(replay = 1)

    override fun hasLocationPermission(): Boolean = true

    override suspend fun getCurrentLocation(): Location =
        Location(
            latitude = 0.0,
            longitude = 0.0,
            altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
        )

    override suspend fun refreshLocation() = Unit
}

/**
 * A [LocationHistoryRepository] that records the last logged record for verification.
 */
private class RecordingLocationHistoryRepository : LocationHistoryRepository {
    var lastRecord: LocationLogRecord? = null

    override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = emptyList()

    override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = emptyFlow()

    override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = emptyList()

    override suspend fun getLocationHistoryBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<LocationHistoryItem> = emptyList()

    override suspend fun getLastLocation(): LocationHistoryItem? = null

    override fun observeLastLocation(): Flow<LocationHistoryItem?> = emptyFlow()

    override suspend fun logLocation(
        location: Location,
        userId: String,
        deviceId: String,
        confidence: Float,
        isGenuine: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun logLocation(record: LocationLogRecord): Result<Unit> {
        lastRecord = record
        return Result.success(Unit)
    }

    override suspend fun deleteLocationEntry(
        userId: String,
        deviceId: String,
        timestamp: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteLocationsBetween(
        startTime: Instant,
        endTime: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getLocationCount(): Int = 0
}
