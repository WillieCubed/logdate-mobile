package app.logdate.wear.presentation.home

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncStatus
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests the sync badge of [WearHomeViewModel], which follows the sync status as it changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearHomeViewModelTest {
    private val status =
        MutableStateFlow(
            SyncStatus(isEnabled = false, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false),
        )
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { notesRepository.observeNotesForDay(any()) } returns flowOf(emptyList())
        every { syncManager.syncStatusFlow } returns status
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the badge follows the sync status as it changes`() =
        runTest {
            val viewModel = WearHomeViewModel(notesRepository, syncManager)

            viewModel.uiState.test {
                assertEquals(SyncBadge.NONE, expectMostRecentItem().syncBadge)

                status.value = status.value.copy(isEnabled = true, pendingUploads = 2)
                assertEquals(SyncBadge.SYNCING, awaitItem().syncBadge)

                status.value = status.value.copy(hasErrors = true)
                assertEquals(SyncBadge.ERROR, awaitItem().syncBadge)

                status.value = status.value.copy(hasErrors = false, pendingUploads = 0)
                assertEquals(SyncBadge.NONE, awaitItem().syncBadge)
            }
        }

    @Test
    fun `items waiting with no phone in reach show no badge`() =
        runTest {
            status.value = status.value.copy(isEnabled = false, pendingUploads = 3)
            val viewModel = WearHomeViewModel(notesRepository, syncManager)

            viewModel.uiState.test {
                assertEquals(SyncBadge.NONE, expectMostRecentItem().syncBadge)
            }
        }

    @Test
    fun `the sync status is never polled`() =
        runTest {
            val viewModel = WearHomeViewModel(notesRepository, syncManager)

            viewModel.uiState.test { expectMostRecentItem() }

            coVerify(exactly = 0) { syncManager.getSyncStatus() }
        }
}
