package app.logdate.wear.presentation.health

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.database.entities.HealthSnapshotEntity
import app.logdate.wear.health.HealthSnapshot
import app.logdate.wear.health.WearHealthSensorManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for the Wear OS Health Dashboard experience.
 *
 * This suite verifies the [HealthDashboardViewModel]'s ability to aggregate and
 * present health-related data. It tests:
 * - Real-time sampling of heart rate and step counts via [WearHealthSensorManager].
 * - Retrieval and display of historical health snapshots from local storage.
 * - Generation of basic correlation insights between health metrics and user notes.
 * - Graceful handling of sensor availability and data loading errors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthDashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var healthSensorManager: WearHealthSensorManager
    private lateinit var healthSnapshotDao: HealthSnapshotDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        healthSensorManager = mockk(relaxed = true)
        healthSnapshotDao = mockk(relaxed = true)

        coEvery { healthSensorManager.sampleCurrent() } returns HealthSnapshot()
        coEvery { healthSnapshotDao.observeRecent(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HealthDashboardViewModel = HealthDashboardViewModel(healthSensorManager, healthSnapshotDao)

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun `initial state has no health data`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.currentHeartRate)
            assertNull(state.currentStepCount)
        }

    // -----------------------------------------------------------------------
    // Current readings
    // -----------------------------------------------------------------------

    @Test
    fun `loads current heart rate from sensor manager`() =
        runTest {
            coEvery { healthSensorManager.sampleCurrent() } returns
                HealthSnapshot(
                    heartRateBpm = 72,
                )
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(72, viewModel.uiState.value.currentHeartRate)
        }

    @Test
    fun `loads current step count from sensor manager`() =
        runTest {
            coEvery { healthSensorManager.sampleCurrent() } returns
                HealthSnapshot(
                    stepCount = 8432,
                )
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(8432, viewModel.uiState.value.currentStepCount)
        }

    @Test
    fun `loads both heart rate and step count`() =
        runTest {
            coEvery { healthSensorManager.sampleCurrent() } returns
                HealthSnapshot(
                    heartRateBpm = 68,
                    stepCount = 5000,
                )
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(68, viewModel.uiState.value.currentHeartRate)
            assertEquals(5000, viewModel.uiState.value.currentStepCount)
        }

    // -----------------------------------------------------------------------
    // Recent snapshots
    // -----------------------------------------------------------------------

    @Test
    fun `loads recent health snapshots from DAO`() =
        runTest {
            val snapshots =
                listOf(
                    HealthSnapshotEntity(
                        id = Uuid.random(),
                        noteId = Uuid.random(),
                        heartRateBpm = 72,
                        stepCount = 5000,
                        timestamp = Instant.fromEpochMilliseconds(1_000_000),
                        source = "wear_health_services",
                    ),
                    HealthSnapshotEntity(
                        id = Uuid.random(),
                        noteId = Uuid.random(),
                        heartRateBpm = 80,
                        stepCount = 6000,
                        timestamp = Instant.fromEpochMilliseconds(900_000),
                        source = "wear_health_services",
                    ),
                )
            coEvery { healthSnapshotDao.observeRecent(any()) } returns flowOf(snapshots)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.recentSnapshots.size)
        }

    // -----------------------------------------------------------------------
    // Correlation insight
    // -----------------------------------------------------------------------

    @Test
    fun `generates correlation insight from snapshots`() =
        runTest {
            val snapshots =
                listOf(
                    HealthSnapshotEntity(
                        id = Uuid.random(),
                        noteId = Uuid.random(),
                        heartRateBpm = 72,
                        timestamp = Instant.fromEpochMilliseconds(1_000_000),
                        source = "wear_health_services",
                    ),
                    HealthSnapshotEntity(
                        id = Uuid.random(),
                        noteId = Uuid.random(),
                        heartRateBpm = 80,
                        timestamp = Instant.fromEpochMilliseconds(900_000),
                        source = "wear_health_services",
                    ),
                )
            coEvery { healthSnapshotDao.observeRecent(any()) } returns flowOf(snapshots)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.correlationInsight
                    .isNotEmpty(),
            )
        }

    @Test
    fun `no correlation insight when no snapshots`() =
        runTest {
            coEvery { healthSnapshotDao.observeRecent(any()) } returns flowOf(emptyList())
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.correlationInsight
                    .isEmpty(),
            )
        }

    // -----------------------------------------------------------------------
    // Error handling
    // -----------------------------------------------------------------------

    @Test
    fun `handles sensor manager error gracefully`() =
        runTest {
            coEvery { healthSensorManager.sampleCurrent() } throws RuntimeException("sensor error")
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.currentHeartRate)
            assertNull(viewModel.uiState.value.currentStepCount)
        }
}
