package app.logdate.feature.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.effectiveReason
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the [SyncPresentation] stream that the timeline / sync surfaces render against.
 *
 * Held in a ViewModel rather than a free function so the chip and banner observe the same
 * cold-flow with proper lifecycle scoping (collection stops when the screen leaves the
 * foreground; sharing means we don't re-collect on every rotation).
 */
class SyncPresentationViewModel(
    syncManager: SyncManager,
    sessionStorage: SessionStorage,
) : ViewModel() {
    val accountStatus: StateFlow<AccountSyncStatus?> =
        combine(syncManager.syncStatusFlow, sessionStorage.getSessionFlow(), syncManager.observeDeadLetters()) { status, session, records ->
            if (session == null) null else accountSyncStatus(status, records.map { it.effectiveReason() }.toSet())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val presentation: StateFlow<SyncPresentation> =
        observeSyncPresentation(syncManager, sessionStorage)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = SyncPresentation.Hidden,
            )
}
