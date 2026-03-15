package app.logdate.feature.core.restore

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class RestoreState {
    data object Idle : RestoreState()

    data object Confirming : RestoreState()

    data object Selecting : RestoreState()

    data object Restoring : RestoreState()

    data class Completed(
        val summary: RestoreSummary,
    ) : RestoreState()

    data class Failed(
        val reason: String,
        val canRetry: Boolean = true,
    ) : RestoreState()
}

class UserDataRestoreViewModel(
    private val restoreLauncher: RestoreLauncher,
) : ViewModel() {
    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    private val _isSheetVisible = MutableStateFlow(false)
    val isSheetVisible: StateFlow<Boolean> = _isSheetVisible.asStateFlow()

    init {
        restoreLauncher.setRestoreCompletionCallback { outcome ->
            when (outcome) {
                is RestoreOutcome.Started -> _restoreState.update { RestoreState.Restoring }
                is RestoreOutcome.Cancelled -> {
                    _restoreState.update { RestoreState.Idle }
                    _isSheetVisible.value = false
                }
                is RestoreOutcome.Success -> {
                    _restoreState.update { RestoreState.Completed(outcome.summary) }
                    _isSheetVisible.value = true
                }
                is RestoreOutcome.Failure -> {
                    _restoreState.update { RestoreState.Failed(outcome.message) }
                    _isSheetVisible.value = true
                }
            }
        }
    }

    fun showRestoreSheet() {
        val current = _restoreState.value
        if (current is RestoreState.Restoring ||
            current is RestoreState.Selecting ||
            current is RestoreState.Completed ||
            current is RestoreState.Failed
        ) {
            _isSheetVisible.value = true
            return
        }
        _restoreState.update { RestoreState.Confirming }
        _isSheetVisible.value = true
    }

    fun confirmRestore() {
        _restoreState.update { RestoreState.Selecting }
        restoreLauncher.startRestore()
    }

    /**
     * Dismisses the bottom sheet. If a restore is in progress, it continues
     * running in the background — only [cancelRestore] stops it.
     */
    fun dismissSheet() {
        val current = _restoreState.value
        _isSheetVisible.value = false
        when (current) {
            is RestoreState.Confirming,
            is RestoreState.Completed,
            is RestoreState.Failed,
            -> _restoreState.update { RestoreState.Idle }
            else -> Unit
        }
    }

    /**
     * Explicitly cancels the running restore. This is the only way to stop
     * an in-progress restore — UI dismissal does not cancel it.
     */
    fun cancelRestore() {
        val current = _restoreState.value
        if (current is RestoreState.Restoring || current is RestoreState.Selecting) {
            restoreLauncher.cancelRestore()
            _restoreState.update { RestoreState.Idle }
            _isSheetVisible.value = false
        }
    }

    fun retryRestore() {
        _restoreState.update { RestoreState.Selecting }
        _isSheetVisible.value = true
        restoreLauncher.startRestore()
    }
}
