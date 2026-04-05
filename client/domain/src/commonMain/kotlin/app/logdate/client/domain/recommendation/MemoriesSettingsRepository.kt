package app.logdate.client.domain.recommendation

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing memories and recommendation preferences.
 */
interface MemoriesSettingsRepository {
    suspend fun getSettings(): MemoriesSettings

    fun observeSettings(): Flow<MemoriesSettings>

    suspend fun updateSettings(settings: MemoriesSettings)

    suspend fun setContextualRecommendationsEnabled(enabled: Boolean) {
        updateSettings(getSettings().copy(contextualRecommendationsEnabled = enabled))
    }

    suspend fun setAmbientPromptsEnabled(enabled: Boolean) {
        updateSettings(getSettings().copy(ambientPromptsEnabled = enabled))
    }

    suspend fun setCaptureNudgesEnabled(enabled: Boolean) {
        updateSettings(getSettings().copy(captureNudgesEnabled = enabled))
    }

    suspend fun setDraftRescueEnabled(enabled: Boolean) {
        updateSettings(getSettings().copy(draftRescueEnabled = enabled))
    }

    suspend fun setMemoryRecallNotificationsEnabled(enabled: Boolean) {
        updateSettings(getSettings().copy(memoryRecallNotificationsEnabled = enabled))
    }

    suspend fun setMorningPromptEnabled(enabled: Boolean) {
        updateSettings(getSettings().copy(morningPromptEnabled = enabled))
    }

    suspend fun setEveningPromptEnabled(enabled: Boolean) {
        updateSettings(getSettings().copy(eveningPromptEnabled = enabled))
    }

    suspend fun setMorningPromptTime(time: AmbientPromptTime) {
        updateSettings(getSettings().copy(morningPromptTime = time))
    }

    suspend fun setEveningPromptTime(time: AmbientPromptTime) {
        updateSettings(getSettings().copy(eveningPromptTime = time))
    }

    suspend fun setAiRecallEnabled(enabled: Boolean) {
        updateSettings(getSettings().copy(aiRecallEnabled = enabled))
    }

    suspend fun setRecallMode(mode: RecallMode) {
        updateSettings(getSettings().copy(recallMode = mode))
    }

    suspend fun setWidgetContentTypes(types: Set<WidgetContentType>) {
        updateSettings(getSettings().copy(widgetContentTypes = types))
    }
}
