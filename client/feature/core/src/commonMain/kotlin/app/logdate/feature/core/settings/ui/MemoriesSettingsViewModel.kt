package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
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
}
