package app.logdate.wear.presentation.settings

import app.cash.turbine.test
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        syncManager = mockk(relaxed = true)
        dataLayerClient = mockk(relaxed = true)
        coEvery { syncManager.getSyncStatus() } returns SyncStatus(
            isEnabled = false,
            lastSyncTime = null,
            pendingUploads = 0,
            isSyncing = false,
            hasErrors = false,
        )
        coEvery { dataLayerClient.getConnectedPhoneName() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WearSettingsViewModel {
        return WearSettingsViewModel(syncManager, dataLayerClient)
    }

    @Test
    fun `initial state is disconnected with no sync info`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isPhoneConnected)
            assertNull(state.phoneName)
            assertNull(state.lastSyncTime)
            assertEquals(0, state.pendingCount)
            assertFalse(state.hasErrors)
            assertFalse(state.isSyncingNow)
            viewModel.stopPolling()
        }
    }

    @Test
    fun `shows connected phone name when phone is reachable`() = runTest {
        coEvery { dataLayerClient.getConnectedPhoneName() } returns "Pixel 9"
        coEvery { syncManager.getSyncStatus() } returns SyncStatus(
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
    fun `shows pending count from sync status`() = runTest {
        coEvery { dataLayerClient.getConnectedPhoneName() } returns "Phone"
        coEvery { syncManager.getSyncStatus() } returns SyncStatus(
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
    fun `shows error state when sync has errors`() = runTest {
        coEvery { syncManager.getSyncStatus() } returns SyncStatus(
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
    fun `syncNow calls fullSync on SyncManager`() = runTest {
        coEvery { syncManager.fullSync() } returns SyncResult(success = true)

        val viewModel = createViewModel()
        viewModel.syncNow()

        coVerify { syncManager.fullSync() }
        viewModel.stopPolling()
    }

    @Test
    fun `disconnected state when phone name is null`() = runTest {
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
