package app.logdate.feature.rewind.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Rewind settings screen. Reads and writes the user's rewind preferences
 * (auto-generation, ready notification) directly through [LogdatePreferencesDataSource].
 */
class RewindSettingsViewModel(
    private val preferences: LogdatePreferencesDataSource,
) : ViewModel() {
    data class UiState(
        val autoGenerationEnabled: Boolean = true,
        val notificationsEnabled: Boolean = true,
        val reflectionRepliesEnabled: Boolean = true,
    )

    val uiState: StateFlow<UiState> =
        combine(
            preferences.observeRewindAutoGenerationEnabled(),
            preferences.observeRewindNotificationsEnabled(),
            preferences.observeRewindReflectionRepliesEnabled(),
        ) { autoGen, notifications, replies ->
            UiState(
                autoGenerationEnabled = autoGen,
                notificationsEnabled = notifications,
                reflectionRepliesEnabled = replies,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(),
        )

    fun setAutoGenerationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setRewindAutoGenerationEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to update rewind auto-generation preference", e)
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setRewindNotificationsEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to update rewind notifications preference", e)
            }
        }
    }

    fun setReflectionRepliesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setRewindReflectionRepliesEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to update rewind reflection replies preference", e)
            }
        }
    }
}
