package app.logdate.client.domain.recommendation

import app.logdate.client.datastore.KeyValueStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Default implementation of [MemoriesSettingsRepository] using [KeyValueStorage].
 */
class DefaultMemoriesSettingsRepository(
    private val keyValueStorage: KeyValueStorage,
) : MemoriesSettingsRepository {
    companion object {
        private const val KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED = "memories_contextual_recommendations_enabled"
        private const val KEY_AI_RECALL_ENABLED = "memories_ai_recall_enabled"
    }

    override suspend fun getSettings(): MemoriesSettings =
        MemoriesSettings(
            contextualRecommendationsEnabled =
                keyValueStorage.getBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, true),
            aiRecallEnabled =
                keyValueStorage.getBoolean(KEY_AI_RECALL_ENABLED, false),
        )

    override fun observeSettings(): Flow<MemoriesSettings> =
        combine(
            keyValueStorage.observeBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, true),
            keyValueStorage.observeBoolean(KEY_AI_RECALL_ENABLED, false),
        ) { contextualEnabled, aiEnabled ->
            MemoriesSettings(
                contextualRecommendationsEnabled = contextualEnabled,
                aiRecallEnabled = aiEnabled,
            )
        }

    override suspend fun updateSettings(settings: MemoriesSettings) {
        Napier.i("Updating memories settings: $settings")
        keyValueStorage.putBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, settings.contextualRecommendationsEnabled)
        keyValueStorage.putBoolean(KEY_AI_RECALL_ENABLED, settings.aiRecallEnabled)
    }

    override suspend fun setContextualRecommendationsEnabled(enabled: Boolean) {
        Napier.i("Setting contextual recommendations enabled: $enabled")
        keyValueStorage.putBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, enabled)
    }

    override suspend fun setAiRecallEnabled(enabled: Boolean) {
        Napier.i("Setting AI recall enabled: $enabled")
        keyValueStorage.putBoolean(KEY_AI_RECALL_ENABLED, enabled)
    }
}
