package app.logdate.client.e2e

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.sync.diagnostics.DiagnosticSource
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.feature.core.restore.ImportOptions
import app.logdate.feature.core.restore.RestoreDiagnosticEvents
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.feature.core.restore.RestoreOutcome
import app.logdate.feature.core.restore.RestoreProgressInfo
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import kotlin.test.assertTrue

internal fun installRestoreWorkerTestBindings(
    restoreUseCase: RestoreUserDataUseCase,
    mediaManager: MediaManager,
    restoreLauncher: RestoreLauncher,
    diagnostics: RestoreDiagnosticEvents? = null,
) {
    val overrides =
        module {
            factory { restoreUseCase }
            single<MediaManager> { mediaManager }
            single<RestoreLauncher> { restoreLauncher }
            single<app.logdate.client.device.identity.CanonicalOwnerProvider> {
                mockk { coEvery { getCanonicalOwnerId() } returns "synthetic-archive-worker" }
            }
            single<app.logdate.shared.config.LogDateConfigRepository> {
                mockk {
                    every { backendUrl } returns MutableStateFlow("https://example.test")
                    every { apiVersion } returns MutableStateFlow("v1")
                    every { serverDescriptor } returns MutableStateFlow<app.logdate.shared.model.ServerDescriptor?>(null)
                    every { getCurrentBackendUrl() } returns "https://example.test"
                }
            }
            single<SessionStorage> { mockk(relaxed = true) }
            single<RestoreDiagnosticEvents> { diagnostics ?: RecordingRestoreDiagnostics() }
        }
    loadKoinModules(overrides)
}

internal class RecordingRestoreDiagnostics : RestoreDiagnosticEvents {
    val events = mutableListOf<SyncDiagnosticEvent>()
    private val source = DiagnosticSource(UploadScope("account", "https://first.example"), "epoch")

    override fun source(): DiagnosticSource = source

    override fun record(
        event: SyncDiagnosticEvent,
        source: DiagnosticSource?,
    ) {
        assertTrue(source === this.source)
        events += event
    }
}

internal class RecordingRestoreLauncher : RestoreLauncher {
    val completedOutcomes = mutableListOf<RestoreOutcome>()

    private val _restoreProgress = MutableStateFlow<RestoreProgressInfo>(RestoreProgressInfo.Idle)
    override val restoreProgress: StateFlow<RestoreProgressInfo> = _restoreProgress.asStateFlow()

    override fun startFileSelection() {}

    override fun startRestore(options: ImportOptions) {}

    override fun cancelRestore() {}

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {}

    override fun setFileSelectedCallback(callback: (app.logdate.feature.core.restore.ArchiveFileInfo?) -> Unit) {}

    override fun updateProgress(info: RestoreProgressInfo) {
        _restoreProgress.value = info
    }

    override fun completeRestore(outcome: RestoreOutcome) {
        completedOutcomes += outcome
    }
}
