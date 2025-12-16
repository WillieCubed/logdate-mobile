package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val settingsRepository: LocationTrackingSettingsRepository
) : ViewModel() {
    
    /**
     * UI state for the location settings screen.
     */
    data class UiState(
        val settings: LocationTrackingSettings = LocationTrackingSettings(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val showLocationTimelineEnabled: Boolean = true
    )
    
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _showLocationTimelineEnabled = MutableStateFlow(true)
    
    /**
     * Current UI state.
     */
    val uiState: StateFlow<UiState> = combine(
        settingsRepository.observeSettings(),
        _isLoading,
        _errorMessage,
        _showLocationTimelineEnabled
    ) { settings, isLoading, errorMessage, showLocationTimelineEnabled ->
        UiState(
            settings = settings,
            isLoading = isLoading,
            errorMessage = errorMessage,
            showLocationTimelineEnabled = showLocationTimelineEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(isLoading = true)
    )
    
    init {
        // Initialize the showLocationTimelineEnabled value from preferences
        viewModelScope.launch {
            try {
                val currentSettings = settingsRepository.getSettings()
                _showLocationTimelineEnabled.value = currentSettings.showLocationTimeline ?: true
            } catch (e: Exception) {
                Napier.e("Failed to load location timeline setting", e)
            }
        }
    }
    
    /**
     * Toggle background location tracking.
     */
    fun toggleBackgroundTracking(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                settingsRepository.setBackgroundTrackingEnabled(enabled)
                Napier.i("Background tracking set to: $enabled")
            } catch (e: Exception) {
                Napier.e("Failed to toggle background tracking", e)
                _errorMessage.value = "Failed to update setting: ${e.message}"
            } finally {
                _isLoading.value = false
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
                    currentSettings.copy(autoTrackForJournalEntries = enabled)
                )
                Napier.i("Journal entry tracking set to: $enabled")
            } catch (e: Exception) {
                Napier.e("Failed to toggle journal tracking", e)
                _errorMessage.value = "Failed to update setting: ${e.message}"
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
                    currentSettings.copy(autoTrackForTimelineReview = enabled)
                )
                Napier.i("Timeline tracking set to: $enabled")
            } catch (e: Exception) {
                Napier.e("Failed to toggle timeline tracking", e)
                _errorMessage.value = "Failed to update setting: ${e.message}"
            }
        }
    }
    
    /**
     * Toggle showing the location timeline.
     */
    fun toggleShowLocationTimeline(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentSettings = settingsRepository.getSettings()
                // Update local state immediately
                _showLocationTimelineEnabled.value = enabled
                // Persist the setting
                settingsRepository.updateSettings(
                    currentSettings.copy(showLocationTimeline = enabled)
                )
                Napier.i("Show location timeline set to: $enabled")
            } catch (e: Exception) {
                Napier.e("Failed to toggle location timeline visibility", e)
                _errorMessage.value = "Failed to update setting: ${e.message}"
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
                _errorMessage.value = "Failed to update interval: ${e.message}"
            }
        }
    }
    
    /**
     * Clear any error message.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}