package app.logdate.client.domain.location

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CaptureLocationForTimelineReviewUseCaseTest {
    @Test
    fun `invoke skips location capture when timeline review tracking is disabled`() =
        runTest {
            val repository = FakeLocationHistoryRepository()
            val useCase =
                CaptureLocationForTimelineReviewUseCase(
                    settingsRepository =
                        FakeLocationTrackingSettingsRepository(
                            LocationTrackingSettings(autoTrackForTimelineReview = false),
                        ),
                    logCurrentLocationUseCase = buildLogCurrentLocationUseCase(repository),
                )

            useCase()

            assertEquals(0, repository.loggedLocations)
        }

    @Test
    fun `invoke captures location when timeline review tracking is enabled`() =
        runTest {
            val repository = FakeLocationHistoryRepository()
            val useCase =
                CaptureLocationForTimelineReviewUseCase(
                    settingsRepository =
                        FakeLocationTrackingSettingsRepository(
                            LocationTrackingSettings(autoTrackForTimelineReview = true),
                        ),
                    logCurrentLocationUseCase = buildLogCurrentLocationUseCase(repository),
                )

            useCase()

            assertEquals(1, repository.loggedLocations)
            assertEquals(LocationCaptureSource.TIMELINE_REVIEW, repository.lastRecord?.captureSource)
        }

    private fun buildLogCurrentLocationUseCase(repository: FakeLocationHistoryRepository): LogCurrentLocationUseCase {
        val locationProvider = FakeLocationProvider()
        return LogCurrentLocationUseCase(
            locationProvider = locationProvider,
            locationHistoryRepository = repository,
            locationRetryWorker =
                LocationRetryWorker(
                    locationProvider = locationProvider,
                    locationHistoryRepository = repository,
                    coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                ),
        )
    }

    private class FakeLocationTrackingSettingsRepository(
        private var settings: LocationTrackingSettings,
    ) : LocationTrackingSettingsRepository {
        override suspend fun getSettings(): LocationTrackingSettings = settings

        override fun observeSettings(): Flow<LocationTrackingSettings> = flowOf(settings)

        override suspend fun updateSettings(settings: LocationTrackingSettings) {
            this.settings = settings
        }

        override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
            settings = settings.copy(backgroundTrackingEnabled = enabled)
        }

        override suspend fun setTrackingInterval(intervalMinutes: Long) {
            settings = settings.copy(minimumPersistIntervalMinutes = intervalMinutes)
        }
    }

    private class FakeLocationProvider : ClientLocationProvider {
        private val location =
            Location(
                latitude = 37.0,
                longitude = -122.0,
                altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
            )

        override val currentLocation: SharedFlow<Location> = MutableSharedFlow(replay = 1)

        override suspend fun getCurrentLocation(): Location = location

        override suspend fun refreshLocation() = Unit
    }

    private class FakeLocationHistoryRepository : LocationHistoryRepository {
        var loggedLocations: Int = 0
        var lastRecord: LocationLogRecord? = null

        override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = emptyList()

        override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = flowOf(emptyList())

        override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = emptyList()

        override suspend fun getLocationHistoryBetween(
            startTime: Instant,
            endTime: Instant,
        ): List<LocationHistoryItem> = emptyList()

        override suspend fun getLastLocation(): LocationHistoryItem? = null

        override fun observeLastLocation(): Flow<LocationHistoryItem?> = flowOf(null)

        override suspend fun logLocation(
            location: Location,
            userId: String,
            deviceId: String,
            confidence: Float,
            isGenuine: Boolean,
        ): Result<Unit> {
            loggedLocations += 1
            return Result.success(Unit)
        }

        override suspend fun logLocation(record: LocationLogRecord): Result<Unit> {
            loggedLocations += 1
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

        override suspend fun getLocationCount(): Int = loggedLocations
    }
}
