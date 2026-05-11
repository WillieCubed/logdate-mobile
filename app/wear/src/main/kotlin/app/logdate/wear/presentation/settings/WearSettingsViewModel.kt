package app.logdate.wear.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.sync.SyncManager
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
    private val locationSettingsRepository: LocationTrackingSettingsRepository,
    private val permissionManager: PermissionManager,
    private val locationTrackingManager: LocationTrackingManager,
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
        pollingJob =
            viewModelScope.launch {
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

    fun setBackgroundLocationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                locationSettingsRepository.setBackgroundTrackingEnabled(enabled)
                locationTrackingManager.startTracking()
                refreshStatus()
            } catch (e: Exception) {
                Napier.w("Failed to update Wear background location setting", e)
            }
        }
    }

    fun setLocationCaptureMode(mode: LocationCaptureMode) {
        viewModelScope.launch {
            try {
                locationSettingsRepository.setCaptureMode(mode)
                locationTrackingManager.startTracking()
                refreshStatus()
            } catch (e: Exception) {
                Napier.w("Failed to update Wear location capture mode", e)
            }
        }
    }

    fun openLocationPermissions() {
        permissionManager.openPermissionSettings()
    }

    private suspend fun refreshStatus() {
        try {
            val phoneName = dataLayerClient.getConnectedPhoneName()
            val syncStatus = syncManager.getSyncStatus()
            val locationSettings = locationSettingsRepository.getSettings()

            _uiState.update {
                it.copy(
                    isPhoneConnected = phoneName != null,
                    phoneName = phoneName,
                    lastSyncTime = syncStatus.lastSyncTime,
                    pendingCount = syncStatus.pendingUploads,
                    hasErrors = syncStatus.hasErrors,
                    locationPermissionGranted = permissionManager.isPermissionGranted(PermissionType.LOCATION),
                    autoTagJournalEntries = locationSettings.autoTrackForJournalEntries,
                    backgroundLocationEnabled = locationSettings.backgroundTrackingEnabled,
                    locationCaptureMode = locationSettings.captureMode,
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
    val locationPermissionGranted: Boolean = false,
    val autoTagJournalEntries: Boolean = true,
    val backgroundLocationEnabled: Boolean = false,
    val locationCaptureMode: LocationCaptureMode = LocationCaptureMode.PASSIVE,
)
