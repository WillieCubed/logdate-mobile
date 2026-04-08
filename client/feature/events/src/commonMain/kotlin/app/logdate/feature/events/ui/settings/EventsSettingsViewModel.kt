package app.logdate.feature.events.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the events hub screen, exposing the two user-facing toggles — master
 * "notice events" and "smart names". The inference worker still reads its sensitivity
 * and run history from preferences; this VM intentionally doesn't surface those.
 */
class EventsSettingsViewModel(
    private val preferences: LogdatePreferencesDataSource,
) : ViewModel() {
    val uiState: StateFlow<EventsSettingsUiState> =
        combine(
            preferences.observeEventsEnabled(),
            preferences.observeEventInferenceAiNamingEnabled(),
        ) { enabled, smartNaming ->
            EventsSettingsUiState(
                isAutoEventsEnabled = enabled,
                isSmartNamingEnabled = smartNaming,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = EventsSettingsUiState(),
        )

    fun setAutoEventsEnabled(enabled: Boolean) {
        if (uiState.value.isAutoEventsEnabled == enabled) return
        viewModelScope.launch {
            preferences.setEventsEnabled(enabled)
        }
    }

    fun setSmartNamingEnabled(enabled: Boolean) {
        if (uiState.value.isSmartNamingEnabled == enabled) return
        viewModelScope.launch {
            preferences.setEventInferenceAiNamingEnabled(enabled)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
