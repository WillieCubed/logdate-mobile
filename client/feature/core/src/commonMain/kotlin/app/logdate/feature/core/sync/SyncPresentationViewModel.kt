package app.logdate.feature.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.SyncManager
import app.logdate.ui.sync.SyncPresentation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

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
) : ViewModel() {
    val presentation: StateFlow<SyncPresentation> =
        observeSyncPresentation(syncManager, sessionStorage)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = SyncPresentation.Hidden,
            )

    /**
     * Starts a backup in response to the timeline's sync affordance being tapped.
     *
     * Tapping it used to do nothing at all: the action reached the screen and was mapped to
     * Unit. An indicator that reports a problem and then ignores being pressed is worse than no
     * indicator, because it teaches people the app is not listening.
     */
    fun retry() {
        syncManager.sync(startNow = true)
    }
}
