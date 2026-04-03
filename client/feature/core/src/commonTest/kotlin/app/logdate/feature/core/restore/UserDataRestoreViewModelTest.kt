package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.PreviewArchiveUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    var startFileSelectionCallCount = 0
        private set
    var startRestoreCallCount = 0
        private set
    var lastRestoreOptions: ImportOptions? = null
        private set
    var cancelRestoreCallCount = 0
        private set

    private var completionCallback: ((RestoreOutcome) -> Unit)? = null
    private var fileSelectedCallback: ((ArchiveFileInfo?) -> Unit)? = null

    private val _restoreProgress = MutableStateFlow<RestoreProgressInfo>(RestoreProgressInfo.Idle)
    override val restoreProgress: StateFlow<RestoreProgressInfo> = _restoreProgress.asStateFlow()

    override fun startFileSelection() {
        startFileSelectionCallCount++
    }

    override fun startRestore(options: ImportOptions) {
        startRestoreCallCount++
        lastRestoreOptions = options
    }

    override fun cancelRestore() {
        cancelRestoreCallCount++
    }

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {
        completionCallback = callback
    }

    override fun setFileSelectedCallback(callback: (ArchiveFileInfo?) -> Unit) {
        fileSelectedCallback = callback
    }

    override fun updateProgress(info: RestoreProgressInfo) {
        _restoreProgress.value = info
    }

    override fun completeRestore(outcome: RestoreOutcome) {
        completionCallback?.invoke(outcome)
    }

    fun triggerOutcome(outcome: RestoreOutcome) {
        completionCallback?.invoke(outcome)
    }

    fun triggerFileSelected(fileInfo: ArchiveFileInfo?) {
        fileSelectedCallback?.invoke(fileInfo)
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

private val testMetadataJson =
    """
    {
        "version": "1.0",
        "exportDate": "2026-03-20T10:00:00Z",
        "userId": "test-user",
        "deviceId": "test-device",
        "appVersion": "2.1.0",
        "stats": {
            "journalCount": 5,
            "noteCount": 42,
            "draftCount": 3,
            "mediaCount": 15
        }
    }
    """.trimIndent()

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

    private fun createViewModel(): UserDataRestoreViewModel = UserDataRestoreViewModel(fakeRestoreLauncher, PreviewArchiveUseCase())

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
    fun `selectFile transitions to Selecting and calls startFileSelection`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()

            assertIs<RestoreState.Selecting>(viewModel.restoreState.value)
            assertEquals(1, fakeRestoreLauncher.startFileSelectionCallCount)
        }

    @Test
    fun `file selected transitions to Previewing with parsed metadata`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo(
                    displayName = "logdate_export.zip",
                    uri = "content://test/file",
                    metadataJson = testMetadataJson,
                ),
            )

            val state = viewModel.restoreState.value
            assertIs<RestoreState.Previewing>(state)
            assertEquals("logdate_export.zip", state.fileName)
            assertEquals(5, state.preview.stats.journalCount)
            assertEquals(42, state.preview.stats.noteCount)
        }

    @Test
    fun `file selection cancelled transitions to Idle`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(null)

            assertIs<RestoreState.Idle>(viewModel.restoreState.value)
            assertFalse(viewModel.isSheetVisible.value)
        }

    @Test
    fun `updateImportOptions updates Previewing state`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo(
                    displayName = "test.zip",
                    uri = "content://test",
                    metadataJson = testMetadataJson,
                ),
            )

            viewModel.updateImportOptions(ImportOptions(includeDrafts = false, includeMedia = false))

            val state = viewModel.restoreState.value
            assertIs<RestoreState.Previewing>(state)
            assertFalse(state.options.includeDrafts)
            assertFalse(state.options.includeMedia)
        }

    @Test
    fun `confirmImport starts restore with selected options`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo(
                    displayName = "test.zip",
                    uri = "content://test",
                    metadataJson = testMetadataJson,
                ),
            )
            viewModel.updateImportOptions(ImportOptions(includeDrafts = false))
            viewModel.confirmImport()

            assertIs<RestoreState.Restoring>(viewModel.restoreState.value)
            assertEquals(1, fakeRestoreLauncher.startRestoreCallCount)
            assertFalse(fakeRestoreLauncher.lastRestoreOptions!!.includeDrafts)
        }

    @Test
    fun `Started callback transitions to Restoring`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )
            viewModel.confirmImport()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)

            assertIs<RestoreState.Restoring>(viewModel.restoreState.value)
        }

    @Test
    fun `Success callback transitions to Completed with summary`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )
            viewModel.confirmImport()
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
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )
            viewModel.confirmImport()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))

            val state = viewModel.restoreState.value
            assertIs<RestoreState.Failed>(state)
            assertEquals(RestoreError.RESTORE_FAILED, state.error)
            assertTrue(viewModel.isSheetVisible.value)
        }

    @Test
    fun `Cancelled callback transitions to Idle and hides sheet`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
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
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )
            viewModel.confirmImport()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)

            viewModel.cancelRestore()

            assertEquals(1, fakeRestoreLauncher.cancelRestoreCallCount)
            assertIs<RestoreState.Idle>(viewModel.restoreState.value)
            assertFalse(viewModel.isSheetVisible.value)
        }

    @Test
    fun `retryRestore transitions to Selecting and opens file picker`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )
            viewModel.confirmImport()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))

            viewModel.retryRestore()

            assertIs<RestoreState.Selecting>(viewModel.restoreState.value)
            assertEquals(2, fakeRestoreLauncher.startFileSelectionCallCount)
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
    fun `dismissSheet from Previewing resets to Idle`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )

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
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )
            viewModel.confirmImport()
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
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )
            viewModel.confirmImport()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            viewModel.dismissSheet()

            assertFalse(viewModel.isSheetVisible.value)

            viewModel.showRestoreSheet()

            assertIs<RestoreState.Restoring>(viewModel.restoreState.value)
            assertTrue(viewModel.isSheetVisible.value)
        }

    @Test
    fun `progress updates flow through to Restoring state`() =
        testScope.runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreSheet()
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )
            viewModel.confirmImport()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)

            fakeRestoreLauncher.updateProgress(
                RestoreProgressInfo.Active(stage = RestoreStage.RESTORING_NOTES, progressPercent = 45),
            )
            advanceUntilIdle()

            val state = viewModel.restoreState.value
            assertIs<RestoreState.Restoring>(state)
            assertEquals(45, state.progressPercent)
            assertEquals(RestoreStage.RESTORING_NOTES, state.stage)
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
            viewModel.selectFile()
            fakeRestoreLauncher.triggerFileSelected(
                ArchiveFileInfo("test.zip", "content://test", testMetadataJson),
            )
            viewModel.confirmImport()
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Started)
            fakeRestoreLauncher.triggerOutcome(RestoreOutcome.Success(summaryWithWarnings))

            val state = viewModel.restoreState.value
            assertIs<RestoreState.Completed>(state)
            assertEquals(2, state.summary.warnings.size)
            assertEquals("Skipped invalid UUID", state.summary.warnings[0])
        }
}
