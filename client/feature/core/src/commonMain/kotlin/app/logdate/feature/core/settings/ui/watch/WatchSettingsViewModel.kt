package app.logdate.feature.core.settings.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.client.domain.watch.WatchSyncSettings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the watch settings hub and detail screens.
 *
 * Exposes the current watch connection state, sync settings, and notification
 * settings. Provides actions for syncing and managing the watch app.
 */
class WatchSettingsViewModel(
    private val connectionManager: WatchConnectionManager,
    private val settingsRepository: WatchSettingsRepository,
) : ViewModel() {
    val connectionState: StateFlow<WatchConnectionState> =
        connectionManager
            .observeConnectionState()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchConnectionState.Loading)

    val syncSettings: StateFlow<WatchSyncSettings> =
        settingsRepository
            .observeSyncSettings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchSyncSettings())

    val notificationSettings: StateFlow<WatchNotificationSettings> =
        settingsRepository
            .observeNotificationSettings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchNotificationSettings())

    fun requestSync() {
        viewModelScope.launch {
            try {
                connectionManager.requestSync()
            } catch (e: Exception) {
                Napier.e(e) { "Failed to request watch sync" }
            }
        }
    }

    fun installAppOnWatch() {
        viewModelScope.launch {
            try {
                connectionManager.installAppOnWatch()
            } catch (e: Exception) {
                Napier.e(e) { "Failed to launch install on watch" }
            }
        }
    }

    fun openAppOnWatch() {
        viewModelScope.launch {
            try {
                connectionManager.openAppOnWatch()
            } catch (e: Exception) {
                Napier.e(e) { "Failed to open app on watch" }
            }
        }
    }

    // Sync settings mutations

    fun setSyncVoiceNotes(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setSyncVoiceNotes(enabled)
            } catch (e: Exception) {
                Napier.e(e) { "Failed to update sync voice notes setting" }
            }
        }
    }

    fun setSyncTextEntries(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setSyncTextEntries(enabled)
            } catch (e: Exception) {
                Napier.e(e) { "Failed to update sync text entries setting" }
            }
        }
    }

    fun setSyncMoodCheckIns(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setSyncMoodCheckIns(enabled)
            } catch (e: Exception) {
                Napier.e(e) { "Failed to update sync mood check-ins setting" }
            }
        }
    }

    fun setSyncHealthData(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setSyncHealthData(enabled)
            } catch (e: Exception) {
                Napier.e(e) { "Failed to update sync health data setting" }
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setAutoSync(enabled)
            } catch (e: Exception) {
                Napier.e(e) { "Failed to update auto-sync setting" }
            }
        }
    }

    // Notification settings mutations

    fun setShowEntryNotifications(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setShowEntryNotifications(enabled)
            } catch (e: Exception) {
                Napier.e(e) { "Failed to update entry notifications setting" }
            }
        }
    }

    fun setIncludeAudioPreview(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setIncludeAudioPreview(enabled)
            } catch (e: Exception) {
                Napier.e(e) { "Failed to update audio preview setting" }
            }
        }
    }
}
