package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the memories settings screens.
 */
class MemoriesSettingsViewModel(
    private val settingsRepository: MemoriesSettingsRepository,
) : ViewModel() {
    data class UiState(
        val settings: MemoriesSettings = MemoriesSettings(),
    )

    val uiState: StateFlow<UiState> =
        settingsRepository
            .observeSettings()
            .map { UiState(settings = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState(),
            )

    fun toggleContextualRecommendations(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setContextualRecommendationsEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle contextual recommendations", e)
            }
        }
    }

    fun toggleAiRecall(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setAiRecallEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle AI recall", e)
            }
        }
    }

    fun setRecallMode(mode: RecallMode) {
        viewModelScope.launch {
            try {
                settingsRepository.setRecallMode(mode)
            } catch (e: Exception) {
                Napier.e("Failed to set recall mode", e)
            }
        }
    }

    fun toggleWidgetContentType(
        type: WidgetContentType,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            try {
                val current = settingsRepository.getSettings().widgetContentTypes
                val updated = if (enabled) current + type else (current - type).ifEmpty { setOf(type) }
                settingsRepository.setWidgetContentTypes(updated)
            } catch (e: Exception) {
                Napier.e("Failed to toggle widget content type", e)
            }
        }
    }
}
