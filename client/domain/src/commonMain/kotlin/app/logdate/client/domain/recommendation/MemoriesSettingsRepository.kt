package app.logdate.client.domain.recommendation

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing memories and recommendation preferences.
 */
interface MemoriesSettingsRepository {
    suspend fun getSettings(): MemoriesSettings

    fun observeSettings(): Flow<MemoriesSettings>

    suspend fun updateSettings(settings: MemoriesSettings)

    suspend fun setContextualRecommendationsEnabled(enabled: Boolean)

    suspend fun setAiRecallEnabled(enabled: Boolean)
}
