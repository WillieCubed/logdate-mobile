package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.PreviewArchiveUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class PartialRecoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val launcher = RecoveryLauncher()
    private val summary =
        RestoreSummary(
            source = "Archive",
            journalsImported = 1,
            notesImported = 1,
            draftsImported = 1,
            journalLinksImported = 1,
            mediaImported = 0,
            unavailableOriginals = 2,
            recoveryId = "recovery",
        )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an unreadable entry retains the source repair action even when media is complete`() =
        runTest(dispatcher) {
            val model = UserDataRestoreViewModel(launcher, PreviewArchiveUseCase())
            val incomplete = summary.copy(unavailableOriginals = 0, needsAnotherArchive = true)
            launcher.completeRestore(RestoreOutcome.Success(incomplete))
            model.dismissSheet()
            model.showRestoreSheet()
            assertEquals(incomplete, assertIs<RestoreState.Completed>(model.restoreState.value).summary)
            model.retryRestore()
            assertEquals("recovery", launcher.recoveryId)
        }

    @Test
    fun `dismissing a partial result retains the recovery action when reopened`() =
        runTest(dispatcher) {
            val model = UserDataRestoreViewModel(launcher, PreviewArchiveUseCase())
            launcher.completeRestore(RestoreOutcome.Success(summary))
            model.dismissSheet()
            assertFalse(model.isSheetVisible.value)
            model.showRestoreSheet()
            assertEquals(summary, assertIs<RestoreState.Completed>(model.restoreState.value).summary)
            model.retryRestore()
            assertEquals("recovery", launcher.recoveryId)
        }

    @Test
    fun `cancelling replacement selection returns to the same partial result`() =
        runTest(dispatcher) {
            val model = UserDataRestoreViewModel(launcher, PreviewArchiveUseCase())
            launcher.completeRestore(RestoreOutcome.Success(summary))
            model.retryRestore()
            launcher.fileCallback?.invoke(null)
            assertEquals(summary, assertIs<RestoreState.Completed>(model.restoreState.value).summary)
            assertTrue(model.isSheetVisible.value)
            model.retryRestore()
            assertEquals("recovery", launcher.recoveryId)
        }

    @Test
    fun `an invalid replacement keeps the original recovery identity for another attempt`() =
        runTest(dispatcher) {
            val model = UserDataRestoreViewModel(launcher, PreviewArchiveUseCase())
            launcher.completeRestore(RestoreOutcome.Success(summary))
            model.retryRestore()
            launcher.fileCallback?.invoke(ArchiveFileInfo("Invalid archive", "content://invalid", "{}"))
            assertEquals("recovery", assertIs<RestoreState.Failed>(model.restoreState.value).recoveryId)
            model.retryRestore()
            assertEquals("recovery", launcher.recoveryId)
        }

    private class RecoveryLauncher : RestoreLauncher {
        var outcomeCallback: ((RestoreOutcome) -> Unit)? = null
        var fileCallback: ((ArchiveFileInfo?) -> Unit)? = null
        var recoveryId: String? = null
        override val restoreProgress = MutableStateFlow<RestoreProgressInfo>(RestoreProgressInfo.Idle)

        override fun startFileSelection() = Unit

        override fun selectArchiveForMediaRecovery(recoveryId: String) {
            this.recoveryId = recoveryId
        }

        override fun startRestore(options: ImportOptions) = Unit

        override fun cancelRestore() = Unit

        override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {
            outcomeCallback = callback
        }

        override fun setFileSelectedCallback(callback: (ArchiveFileInfo?) -> Unit) {
            fileCallback = callback
        }

        override fun updateProgress(info: RestoreProgressInfo) = Unit

        override fun completeRestore(outcome: RestoreOutcome) {
            outcomeCallback?.invoke(outcome)
        }
    }
}
