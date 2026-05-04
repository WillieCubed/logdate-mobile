package app.logdate.feature.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun retry(id: String) {
        viewModelScope.launch { syncManager.retryDeadLetter(id) }
    }

    fun discard(id: String) {
        viewModelScope.launch { syncManager.discardDeadLetter(id) }
    }
}
