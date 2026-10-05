package app.logdate.feature.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.effectiveReason
import app.logdate.feature.core.settings.ui.CloudArchivePhase
import app.logdate.feature.core.settings.ui.CloudArchiveStatus
import app.logdate.feature.core.settings.ui.CloudArchiveStatusSource
import app.logdate.feature.core.settings.ui.backupStatus
import app.logdate.feature.core.settings.ui.scopedCloudArchiveStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Exposes the [SyncPresentation] stream that the timeline / sync surfaces render against.
 *
 * Held in a ViewModel rather than a free function so the chip and banner observe the same
 * cold-flow with proper lifecycle scoping (collection stops when the screen leaves the
 * foreground; sharing means we don't re-collect on every rotation).
 */
class SyncPresentationViewModel(
    private val syncManager: SyncManager,
    sessionStorage: SessionStorage,
    cloudArchiveStatusSource: CloudArchiveStatusSource? = null,
) : ViewModel() {
    private val sessionFlow = sessionStorage.getSessionFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val archiveStatusFlow =
        sessionFlow.flatMapLatest { session ->
            if (session == null || cloudArchiveStatusSource == null) {
                flowOf(null to null)
            } else {
                cloudArchiveStatusSource.observe(session).map { session.accountId to it }
            }
        }

    val accountStatus: StateFlow<AccountSyncStatus?> =
        combine(syncManager.syncStatusFlow, sessionFlow, syncManager.observeDeadLetters(), archiveStatusFlow) {
            status,
            session,
            records,
            archive,
            ->
            if (session == null) {
                null
            } else {
                val scopedArchive =
                    if (cloudArchiveStatusSource == null) {
                        null
                    } else {
                        scopedCloudArchiveStatus(
                            session.accountId,
                            archive.first,
                            archive.second ?: CloudArchiveStatus(CloudArchivePhase.CHECKING),
                        )
                    }
                backupStatus(status, scopedArchive, records.map { it.effectiveReason() }.toSet())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun useMobileData() {
        viewModelScope.launch {
            try {
                syncManager.requestBackup(allowMeteredMedia = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Napier.e("Could not request sync using mobile data")
            }
        }
    }

    val presentation: StateFlow<SyncPresentation> =
        observeSyncPresentation(syncManager, sessionStorage)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = SyncPresentation.Hidden,
            )
}
