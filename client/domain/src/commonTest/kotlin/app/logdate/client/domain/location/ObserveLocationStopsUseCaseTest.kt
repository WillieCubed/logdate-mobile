package app.logdate.client.domain.location

import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ObserveLocationStopsUseCaseTest {
    @Test
    fun `invoke groups nearby consecutive points into one stop`() =
        runTest {
            val baseTime = Instant.fromEpochMilliseconds(1_000)
            val repository =
                FakeLocationHistoryRepository(
                    listOf(
                        historyItem(timestamp = baseTime, latitude = 37.7749, longitude = -122.4194),
                        historyItem(timestamp = baseTime + 5.minutes, latitude = 37.7750, longitude = -122.4195),
                        historyItem(timestamp = baseTime + 10.minutes, latitude = 37.7751, longitude = -122.4196),
                    ),
                )

            val useCase = ObserveLocationStopsUseCase(ObserveLocationHistoryUseCase(repository))

            val result = useCase().first()

            assertEquals(1, result.size)
            assertEquals(3, result.first().sampleCount)
            assertEquals(baseTime, result.first().startTime)
            assertEquals(baseTime + 10.minutes, result.first().endTime)
            assertEquals(LocationStopEvidenceKind.STAY, result.first().evidenceKind)
            assertEquals(true, result.first().hasReliableDuration)
        }

    @Test
    fun `invoke splits stops when time gap exceeds threshold`() =
        runTest {
            val baseTime = Instant.fromEpochMilliseconds(1_000)
            val repository =
                FakeLocationHistoryRepository(
                    listOf(
                        historyItem(timestamp = baseTime, latitude = 37.7749, longitude = -122.4194),
                        historyItem(timestamp = baseTime + 5.minutes, latitude = 37.7750, longitude = -122.4195),
                        historyItem(timestamp = baseTime + 45.minutes, latitude = 37.7750, longitude = -122.4195),
                    ),
                )

            val useCase = ObserveLocationStopsUseCase(ObserveLocationHistoryUseCase(repository))

            val result = useCase().first()

            assertEquals(2, result.size)
            assertEquals(baseTime + 45.minutes, result.first().startTime)
            assertEquals(2, result.last().sampleCount)
            assertEquals(LocationStopEvidenceKind.OBSERVATION, result.first().evidenceKind)
        }

    @Test
    fun `invoke splits stops when distance exceeds threshold`() =
        runTest {
            val baseTime = Instant.fromEpochMilliseconds(1_000)
            val repository =
                FakeLocationHistoryRepository(
                    listOf(
                        historyItem(timestamp = baseTime, latitude = 37.7749, longitude = -122.4194),
                        historyItem(timestamp = baseTime + 5.minutes, latitude = 37.8044, longitude = -122.2711),
                    ),
                )

            val useCase = ObserveLocationStopsUseCase(ObserveLocationHistoryUseCase(repository))

            val result = useCase().first()

            assertEquals(2, result.size)
            assertEquals(1, result.first().sampleCount)
            assertEquals(1, result.last().sampleCount)
            assertEquals(LocationStopEvidenceKind.OBSERVATION, result.first().evidenceKind)
            assertEquals(false, result.first().hasReliableDuration)
        }

    @Test
    fun `invoke excludes timeline review captures from activity stops`() =
        runTest {
            val baseTime = Instant.fromEpochMilliseconds(1_000)
            val repository =
                FakeLocationHistoryRepository(
                    listOf(
                        historyItem(
                            timestamp = baseTime,
                            latitude = 37.7749,
                            longitude = -122.4194,
                            captureSource = app.logdate.client.repository.location.LocationCaptureSource.TIMELINE_REVIEW,
                        ),
                        historyItem(
                            timestamp = baseTime + 2.minutes,
                            latitude = 37.7750,
                            longitude = -122.4195,
                        ),
                    ),
                )

            val useCase = ObserveLocationStopsUseCase(ObserveLocationHistoryUseCase(repository))

            val result = useCase().first()

            assertEquals(1, result.size)
            assertEquals(1, result.first().sampleCount)
            assertEquals(LocationStopEvidenceKind.OBSERVATION, result.first().evidenceKind)
        }

    private fun historyItem(
        timestamp: Instant,
        latitude: Double,
        longitude: Double,
        captureSource: app.logdate.client.repository.location.LocationCaptureSource =
            app.logdate.client.repository.location.LocationCaptureSource.BACKGROUND_PERIODIC,
    ) = LocationHistoryItem(
        userId = "user",
        deviceId = "device",
        timestamp = timestamp,
        location =
            Location(
                latitude = latitude,
                longitude = longitude,
                altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
            ),
        confidence = 1.0f,
        isGenuine = true,
        captureSource = captureSource,
    )

    private class FakeLocationHistoryRepository(
        history: List<LocationHistoryItem>,
    ) : LocationHistoryRepository {
        private val state = MutableStateFlow(history)

        override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = state.value

        override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = state

        override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = state.value.take(limit)

        override suspend fun getLocationHistoryBetween(
            startTime: Instant,
            endTime: Instant,
        ): List<LocationHistoryItem> = state.value.filter { it.timestamp in startTime..endTime }

        override suspend fun getLastLocation(): LocationHistoryItem? = state.value.maxByOrNull { it.timestamp }

        override fun observeLastLocation(): Flow<LocationHistoryItem?> = flowOf(state.value.maxByOrNull { it.timestamp })

        override suspend fun logLocation(
            location: Location,
            userId: String,
            deviceId: String,
            confidence: Float,
            isGenuine: Boolean,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun logLocation(record: LocationLogRecord): Result<Unit> = Result.success(Unit)

        override suspend fun deleteLocationEntry(
            userId: String,
            deviceId: String,
            timestamp: Instant,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteLocationsBetween(
            startTime: Instant,
            endTime: Instant,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun getLocationCount(): Int = state.value.size
    }
}
