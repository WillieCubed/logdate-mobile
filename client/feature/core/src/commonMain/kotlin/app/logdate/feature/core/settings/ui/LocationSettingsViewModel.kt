@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for managing location tracking settings.
 */
class LocationSettingsViewModel(
    private val settingsRepository: LocationTrackingSettingsRepository,
) : ViewModel() {
    /**
     * UI state for the location settings screen.
     */
    data class UiState(
        val settings: LocationTrackingSettings = LocationTrackingSettings(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    private val isLoadingState = MutableStateFlow(false)
    private val errorMessageState = MutableStateFlow<String?>(null)

    /**
     * Current UI state.
     */
    val uiState: StateFlow<UiState> =
        combine(
            settingsRepository.observeSettings(),
            isLoadingState,
            errorMessageState,
        ) { settings, isLoading, errorMessage ->
            UiState(
                settings = settings,
                isLoading = isLoading,
                errorMessage = errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(isLoading = true),
        )

    /**
     * Toggle background location tracking.
     */
    fun toggleBackgroundTracking(enabled: Boolean) {
        viewModelScope.launch {
            try {
                isLoadingState.value = true
                settingsRepository.setBackgroundTrackingEnabled(enabled)
                Napier.i("Background tracking set to: $enabled")
            } catch (e: Exception) {
                Napier.e("Failed to toggle background tracking", e)
                errorMessageState.value = "Failed to update setting: ${e.message}"
            } finally {
                isLoadingState.value = false
            }
        }
    }

    /**
     * Toggle auto-tracking for journal entries.
     */
    fun toggleJournalTracking(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentSettings = settingsRepository.getSettings()
                settingsRepository.updateSettings(
                    currentSettings.copy(autoTrackForJournalEntries = enabled),
                )
                Napier.i("Journal entry tracking set to: $enabled")
            } catch (e: Exception) {
                Napier.e("Failed to toggle journal tracking", e)
                errorMessageState.value = "Failed to update setting: ${e.message}"
            }
        }
    }

    /**
     * Toggle auto-tracking for timeline viewing.
     */
    fun toggleTimelineTracking(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentSettings = settingsRepository.getSettings()
                settingsRepository.updateSettings(
                    currentSettings.copy(autoTrackForTimelineReview = enabled),
                )
                Napier.i("Timeline tracking set to: $enabled")
            } catch (e: Exception) {
                Napier.e("Failed to toggle timeline tracking", e)
                errorMessageState.value = "Failed to update setting: ${e.message}"
            }
        }
    }

    /**
     * Update the tracking interval.
     */
    fun updateTrackingInterval(intervalMinutes: Long) {
        viewModelScope.launch {
            try {
                settingsRepository.setTrackingInterval(intervalMinutes)
                Napier.i("Tracking interval set to: $intervalMinutes minutes")
            } catch (e: Exception) {
                Napier.e("Failed to update tracking interval", e)
                errorMessageState.value = "Failed to update interval: ${e.message}"
            }
        }
    }

    fun toggleActiveTracking(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setCaptureMode(
                    if (enabled) {
                        LocationCaptureMode.ACTIVE
                    } else {
                        LocationCaptureMode.PASSIVE
                    },
                )
                Napier.i("Active tracking set to: $enabled")
            } catch (e: Exception) {
                Napier.e("Failed to update capture mode", e)
                errorMessageState.value = "Failed to update capture mode: ${e.message}"
            }
        }
    }

    fun setCaptureMode(mode: LocationCaptureMode) {
        viewModelScope.launch {
            try {
                settingsRepository.setCaptureMode(mode)
                Napier.i("Capture mode set to: $mode")
            } catch (e: Exception) {
                Napier.e("Failed to update capture mode", e)
                errorMessageState.value = "Failed to update capture mode: ${e.message}"
            }
        }
    }

    fun toggleServerAssist(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setServerAssistEnabled(enabled)
                Napier.i("Server assist set to: $enabled")
            } catch (e: Exception) {
                Napier.e("Failed to update server assist", e)
                errorMessageState.value = "Failed to update server assist: ${e.message}"
            }
        }
    }

    /**
     * Clear any error message.
     */
    fun clearErrorMessage() {
        errorMessageState.value = null
    }
}
