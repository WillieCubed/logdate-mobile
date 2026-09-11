package app.logdate.feature.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncIssuesViewModel(
    private val syncManager: SyncManager,
) : ViewModel() {
    val records: StateFlow<List<SyncDeadLetterRecord>> =
        syncManager
            .observeDeadLetters()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * Entries still waiting to upload. Dead-lettering takes nine failed attempts, so a queue that
     * is stuck but has not exhausted its retries would otherwise render as "everything is synced"
     * while nothing has actually been backed up.
     */
    val pendingCount: StateFlow<Int> =
        syncManager.syncStatusFlow
            .map { it.pendingUploads }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    fun retry(id: String) {
        viewModelScope.launch { syncManager.retryDeadLetter(id) }
    }

    fun discard(id: String) {
        viewModelScope.launch { syncManager.discardDeadLetter(id) }
    }
}
