package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.recommendation.AmbientPromptTime
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

    fun toggleAmbientPrompts(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setAmbientPromptsEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle ambient prompts", e)
            }
        }
    }

    fun toggleCaptureNudges(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setCaptureNudgesEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle capture nudges", e)
            }
        }
    }

    fun toggleDraftRescue(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setDraftRescueEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle draft rescue", e)
            }
        }
    }

    fun toggleMemoryRecallNotifications(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setMemoryRecallNotificationsEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle memory recall notifications", e)
            }
        }
    }

    fun toggleMorningPrompt(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setMorningPromptEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle morning prompt", e)
            }
        }
    }

    fun toggleEveningPrompt(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setEveningPromptEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle evening prompt", e)
            }
        }
    }

    fun setMorningPromptTime(time: AmbientPromptTime) {
        viewModelScope.launch {
            try {
                settingsRepository.setMorningPromptTime(time)
            } catch (e: Exception) {
                Napier.e("Failed to set morning prompt time", e)
            }
        }
    }

    fun setEveningPromptTime(time: AmbientPromptTime) {
        viewModelScope.launch {
            try {
                settingsRepository.setEveningPromptTime(time)
            } catch (e: Exception) {
                Napier.e("Failed to set evening prompt time", e)
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
