package app.logdate.wear.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncStatus
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * ViewModel backing the Wear OS Settings screen.
 *
 * Polls phone connection status every 5 seconds and observes
 * sync state from [SyncManager].
 */
class WearSettingsViewModel(
    private val syncManager: SyncManager,
    private val dataLayerClient: WearDataLayerClient,
) : ViewModel() {

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }

    private val _uiState = MutableStateFlow(WearSettingsUiState())
    val uiState: StateFlow<WearSettingsUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPolling()
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshStatus()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingNow = true) }
            try {
                syncManager.fullSync()
            } catch (e: Exception) {
                Napier.w("Sync Now failed", e)
            } finally {
                refreshStatus()
                _uiState.update { it.copy(isSyncingNow = false) }
            }
        }
    }

    private suspend fun refreshStatus() {
        try {
            val phoneName = dataLayerClient.getConnectedPhoneName()
            val syncStatus = syncManager.getSyncStatus()

            _uiState.update {
                it.copy(
                    isPhoneConnected = phoneName != null,
                    phoneName = phoneName,
                    lastSyncTime = syncStatus.lastSyncTime,
                    pendingCount = syncStatus.pendingUploads,
                    hasErrors = syncStatus.hasErrors,
                )
            }
        } catch (e: Exception) {
            Napier.w("Failed to refresh settings status", e)
        }
    }
}

data class WearSettingsUiState(
    val isPhoneConnected: Boolean = false,
    val phoneName: String? = null,
    val lastSyncTime: Instant? = null,
    val pendingCount: Int = 0,
    val hasErrors: Boolean = false,
    val isSyncingNow: Boolean = false,
)
