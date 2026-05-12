package app.logdate.feature.rewind.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.intelligence.curation.CurationConfig
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Rewind settings screen. Reads and writes the user's rewind preferences
 * (auto-generation, ready notification, reflection-prompt replies, curation strictness,
 * and screenshot inclusion) directly through [LogdatePreferencesDataSource].
 */
class RewindSettingsViewModel(
    private val preferences: LogdatePreferencesDataSource,
) : ViewModel() {
    data class UiState(
        val autoGenerationEnabled: Boolean = true,
        val notificationsEnabled: Boolean = true,
        val reflectionRepliesEnabled: Boolean = true,
        val curationStrictness: CurationConfig.Strictness = CurationConfig.Strictness.STANDARD,
        val includeScreenshots: Boolean = false,
    )

    val uiState: StateFlow<UiState> =
        combine(
            preferences.observeRewindAutoGenerationEnabled(),
            preferences.observeRewindNotificationsEnabled(),
            preferences.observeRewindReflectionRepliesEnabled(),
            preferences.observeRewindCurationStrictness(),
            preferences.observeRewindIncludeScreenshots(),
        ) { autoGen, notifications, replies, strictness, includeScreenshots ->
            UiState(
                autoGenerationEnabled = autoGen,
                notificationsEnabled = notifications,
                reflectionRepliesEnabled = replies,
                curationStrictness =
                    runCatching { CurationConfig.Strictness.valueOf(strictness) }
                        .getOrDefault(CurationConfig.Strictness.STANDARD),
                includeScreenshots = includeScreenshots,
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

    fun setCurationStrictness(strictness: CurationConfig.Strictness) {
        viewModelScope.launch {
            try {
                preferences.setRewindCurationStrictness(strictness.name)
            } catch (e: Exception) {
                Napier.e("Failed to update rewind curation strictness preference", e)
            }
        }
    }

    fun setIncludeScreenshots(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setRewindIncludeScreenshots(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to update rewind include-screenshots preference", e)
            }
        }
    }
}
