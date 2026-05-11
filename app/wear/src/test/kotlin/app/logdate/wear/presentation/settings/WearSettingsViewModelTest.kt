package app.logdate.wear.presentation.settings

import app.cash.turbine.test
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class WearSettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var syncManager: SyncManager
    private lateinit var dataLayerClient: WearDataLayerClient
    private lateinit var locationSettingsRepository: LocationTrackingSettingsRepository
    private lateinit var permissionManager: PermissionManager
    private lateinit var locationTrackingManager: LocationTrackingManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        syncManager = mockk(relaxed = true)
        dataLayerClient = mockk(relaxed = true)
        locationSettingsRepository = mockk(relaxed = true)
        permissionManager = mockk(relaxed = true)
        locationTrackingManager = mockk(relaxed = true)
        coEvery { syncManager.getSyncStatus() } returns
            SyncStatus(
                isEnabled = false,
                lastSyncTime = null,
                pendingUploads = 0,
                isSyncing = false,
                hasErrors = false,
            )
        coEvery { dataLayerClient.getConnectedPhoneName() } returns null
        coEvery { locationSettingsRepository.getSettings() } returns LocationTrackingSettings()
        coEvery { locationSettingsRepository.setBackgroundTrackingEnabled(any()) } returns Unit
        coEvery { locationSettingsRepository.setCaptureMode(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WearSettingsViewModel =
        WearSettingsViewModel(
            syncManager,
            dataLayerClient,
            locationSettingsRepository,
            permissionManager,
            locationTrackingManager,
        )

    @Test
    fun `initial state is disconnected with no sync info`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                val state = awaitItem()
                assertFalse(state.isPhoneConnected)
                assertNull(state.phoneName)
                assertNull(state.lastSyncTime)
                assertEquals(0, state.pendingCount)
                assertFalse(state.hasErrors)
                assertFalse(state.isSyncingNow)
                assertTrue(state.autoTagJournalEntries)
                assertFalse(state.backgroundLocationEnabled)
                assertEquals(LocationCaptureMode.PASSIVE, state.locationCaptureMode)
                viewModel.stopPolling()
            }
        }

    @Test
    fun `initial state reflects location settings and foreground permission`() =
        runTest {
            coEvery { locationSettingsRepository.getSettings() } returns
                LocationTrackingSettings(
                    backgroundTrackingEnabled = true,
                    captureMode = LocationCaptureMode.ACTIVE,
                    autoTrackForJournalEntries = true,
                )
            coEvery { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns true

            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertTrue(state.locationPermissionGranted)
                assertTrue(state.backgroundLocationEnabled)
                assertEquals(LocationCaptureMode.ACTIVE, state.locationCaptureMode)
                assertTrue(state.autoTagJournalEntries)
                viewModel.stopPolling()
            }
        }

    @Test
    fun `setBackgroundLocationEnabled persists setting and reapplies tracking`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.setBackgroundLocationEnabled(true)

            coVerify { locationSettingsRepository.setBackgroundTrackingEnabled(true) }
            verify { locationTrackingManager.startTracking() }
            viewModel.stopPolling()
        }

    @Test
    fun `setLocationCaptureMode persists mode and reapplies tracking`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.setLocationCaptureMode(LocationCaptureMode.ACTIVE)

            coVerify { locationSettingsRepository.setCaptureMode(LocationCaptureMode.ACTIVE) }
            verify { locationTrackingManager.startTracking() }
            viewModel.stopPolling()
        }

    @Test
    fun `openLocationPermissions opens app permission settings`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.openLocationPermissions()

            verify { permissionManager.openPermissionSettings() }
            viewModel.stopPolling()
        }

    @Test
    fun `shows connected phone name when phone is reachable`() =
        runTest {
            coEvery { dataLayerClient.getConnectedPhoneName() } returns "Pixel 9"
            coEvery { syncManager.getSyncStatus() } returns
                SyncStatus(
                    isEnabled = true,
                    lastSyncTime = Clock.System.now(),
                    pendingUploads = 0,
                    isSyncing = false,
                    hasErrors = false,
                )

            val viewModel = createViewModel()
            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertTrue(state.isPhoneConnected)
                assertEquals("Pixel 9", state.phoneName)
                viewModel.stopPolling()
            }
        }

    @Test
    fun `shows pending count from sync status`() =
        runTest {
            coEvery { dataLayerClient.getConnectedPhoneName() } returns "Phone"
            coEvery { syncManager.getSyncStatus() } returns
                SyncStatus(
                    isEnabled = true,
                    lastSyncTime = null,
                    pendingUploads = 3,
                    isSyncing = false,
                    hasErrors = false,
                )

            val viewModel = createViewModel()
            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(3, state.pendingCount)
                viewModel.stopPolling()
            }
        }

    @Test
    fun `shows error state when sync has errors`() =
        runTest {
            coEvery { syncManager.getSyncStatus() } returns
                SyncStatus(
                    isEnabled = true,
                    lastSyncTime = null,
                    pendingUploads = 0,
                    isSyncing = false,
                    hasErrors = true,
                )

            val viewModel = createViewModel()
            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertTrue(state.hasErrors)
                viewModel.stopPolling()
            }
        }

    @Test
    fun `syncNow calls fullSync on SyncManager`() =
        runTest {
            coEvery { syncManager.fullSync() } returns SyncResult(success = true)

            val viewModel = createViewModel()
            viewModel.syncNow()

            coVerify { syncManager.fullSync() }
            viewModel.stopPolling()
        }

    @Test
    fun `disconnected state when phone name is null`() =
        runTest {
            coEvery { dataLayerClient.getConnectedPhoneName() } returns null

            val viewModel = createViewModel()
            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertFalse(state.isPhoneConnected)
                assertNull(state.phoneName)
                viewModel.stopPolling()
            }
        }
}
