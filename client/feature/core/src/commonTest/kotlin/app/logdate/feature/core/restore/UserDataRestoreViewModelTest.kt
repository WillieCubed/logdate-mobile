package app.logdate.feature.core.restore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeRestoreLauncher : RestoreLauncher {
    var startRestoreCallCount = 0
        private set
    var cancelRestoreCallCount = 0
        private set
    private var completionCallback: ((RestoreOutcome) -> Unit)? = null

    override fun startRestore() {
        startRestoreCallCount++
    }

    override fun cancelRestore() {
        cancelRestoreCallCount++
    }

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {
        completionCallback = callback
    }

    fun triggerOutcome(outcome: RestoreOutcome) {
        completionCallback?.invoke(outcome)
    }
}

private val testSummary =
    RestoreSummary(
        source = "/path/to/backup.zip",
        journalsImported = 3,
        notesImported = 12,
        draftsImported = 2,
        journalLinksImported = 5,
        mediaImported = 8,
        warnings = emptyList(),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class UserDataRestoreViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeRestoreLauncher: FakeRestoreLauncher
    private lateinit var viewModel: UserDataRestoreViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRestoreLauncher = FakeRestoreLauncher()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UserDataRestoreViewModel = UserDataRestoreViewModel(fakeRestoreLauncher)

    @Test
    fun `initial state is Idle and sheet not visible`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertIs<RestoreState.Idle>(viewModel.restoreState.value)
            assertFalse(viewModel.isSheetVisible.value)
        }

    @Test
    fun `showRestoreSheet transitions to Confirming`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()

            assertIs<RestoreState.Confirming>(viewModel.restoreState.value)
            assertTrue(viewModel.isSheetVisible.value)
        }

    @Test
    fun `confirmRestore transitions to Selecting and calls startRestore`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()

            assertIs<RestoreState.Selecting>(viewModel.restoreState.value)
            assertEquals(1, fakeRestoreLauncher.startRestoreCallCount)
        }

    @Test
    fun `Started callback transitions to Restoring`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)

            assertIs<RestoreState.Restoring>(viewModel.restoreState.value)
        }

    @Test
    fun `Success callback transitions to Completed with summary`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Success(testSummary))

            val state = viewModel.restoreState.value
            assertIs<RestoreState.Completed>(state)
            assertEquals(testSummary, state.summary)
            assertTrue(viewModel.isSheetVisible.value)
        }

    @Test
    fun `Failure callback transitions to Failed`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Failure("Corrupted archive"))

            val state = viewModel.restoreState.value
            assertIs<RestoreState.Failed>(state)
            assertEquals("Corrupted archive", state.reason)
            assertTrue(viewModel.isSheetVisible.value)
        }

    @Test
    fun `Cancelled callback transitions to Idle and hides sheet`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Cancelled)

            assertIs<RestoreState.Idle>(viewModel.restoreState.value)
            assertFalse(viewModel.isSheetVisible.value)
        }

    @Test
    fun `cancelRestore calls launcher and transitions to Idle`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)

            viewModel.cancelRestore()

            assertEquals(1, fakeRestoreLauncher.cancelRestoreCallCount)
            assertIs<RestoreState.Idle>(viewModel.restoreState.value)
            assertFalse(viewModel.isSheetVisible.value)
        }

    @Test
    fun `retryRestore transitions to Selecting and calls startRestore again`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Failure("Error"))

            viewModel.retryRestore()

            assertIs<RestoreState.Selecting>(viewModel.restoreState.value)
            assertEquals(2, fakeRestoreLauncher.startRestoreCallCount)
            assertTrue(viewModel.isSheetVisible.value)
        }

    @Test
    fun `dismissSheet from Confirming resets to Idle`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.dismissSheet()

            assertIs<RestoreState.Idle>(viewModel.restoreState.value)
            assertFalse(viewModel.isSheetVisible.value)
        }

    @Test
    fun `dismissSheet from Completed resets to Idle`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Success(testSummary))

            viewModel.dismissSheet()

            assertIs<RestoreState.Idle>(viewModel.restoreState.value)
            assertFalse(viewModel.isSheetVisible.value)
        }

    @Test
    fun `dismissSheet from Failed resets to Idle`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Failure("Error"))

            viewModel.dismissSheet()

            assertIs<RestoreState.Idle>(viewModel.restoreState.value)
            assertFalse(viewModel.isSheetVisible.value)
        }

    @Test
    fun `dismissSheet during Restoring keeps state unchanged`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)

            viewModel.dismissSheet()

            assertIs<RestoreState.Restoring>(viewModel.restoreState.value)
            assertFalse(viewModel.isSheetVisible.value)
        }

    @Test
    fun `showRestoreSheet during Restoring just re-shows sheet`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            viewModel.dismissSheet()

            assertFalse(viewModel.isSheetVisible.value)

            viewModel.showRestoreSheet()

            assertIs<RestoreState.Restoring>(viewModel.restoreState.value)
            assertTrue(viewModel.isSheetVisible.value)
        }

    @Test
    fun `Success with warnings propagates warnings in summary`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val summaryWithWarnings =
                testSummary.copy(
                    warnings = listOf("Skipped invalid UUID", "Media file not found"),
                )

            viewModel.showRestoreSheet()
            viewModel.confirmRestore()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Success(summaryWithWarnings))

            val state = viewModel.restoreState.value
            assertIs<RestoreState.Completed>(state)
            assertEquals(2, state.summary.warnings.size)
            assertEquals("Skipped invalid UUID", state.summary.warnings[0])
        }
}
