package app.logdate.feature.core.settings.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.domain.watch.WatchSettingsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the watch settings hub and detail screens.
 *
 * Exposes the current watch connection state and notification settings, and provides actions
 * for pairing, syncing and managing the watch app.
 */
class WatchSettingsViewModel(
    private val connectionManager: WatchConnectionManager,
    private val settingsRepository: WatchSettingsRepository,
) : ViewModel() {
    val connectionState: StateFlow<WatchConnectionState> =
        connectionManager
            .observeConnectionState()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchConnectionState.Loading)

    val notificationSettings: StateFlow<WatchNotificationSettings> =
        settingsRepository
            .observeNotificationSettings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchNotificationSettings())

    fun beginAssociation() {
        viewModelScope.launch {
            try {
                connectionManager.beginAssociation()
            } catch (e: Exception) {
                Napier.e(e) { "Failed to begin watch association" }
            }
        }
    }

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
}
