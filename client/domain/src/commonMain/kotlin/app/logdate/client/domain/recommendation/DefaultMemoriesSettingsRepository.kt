package app.logdate.client.domain.recommendation

import app.logdate.client.datastore.KeyValueStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Default implementation of [MemoriesSettingsRepository] using [KeyValueStorage].
 */
class DefaultMemoriesSettingsRepository(
    private val keyValueStorage: KeyValueStorage,
) : MemoriesSettingsRepository {
    companion object {
        private const val KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED = "memories_contextual_recommendations_enabled"
        private const val KEY_AI_RECALL_ENABLED = "memories_ai_recall_enabled"
        private const val KEY_RECALL_MODE = "memories_recall_mode"
        private const val KEY_WIDGET_CONTENT_TYPES = "memories_widget_content_types"
    }

    override suspend fun getSettings(): MemoriesSettings =
        MemoriesSettings(
            contextualRecommendationsEnabled =
                keyValueStorage.getBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, true),
            aiRecallEnabled =
                keyValueStorage.getBoolean(KEY_AI_RECALL_ENABLED, false),
            recallMode = loadRecallMode(),
            widgetContentTypes = loadWidgetContentTypes(),
        )

    override fun observeSettings(): Flow<MemoriesSettings> =
        combine(
            keyValueStorage.observeBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, true),
            keyValueStorage.observeBoolean(KEY_AI_RECALL_ENABLED, false),
            keyValueStorage.observeString(KEY_RECALL_MODE).map { parseRecallMode(it) },
            keyValueStorage.observeString(KEY_WIDGET_CONTENT_TYPES).map { parseContentTypes(it) },
        ) { contextualEnabled, aiEnabled, recallMode, contentTypes ->
            MemoriesSettings(
                contextualRecommendationsEnabled = contextualEnabled,
                aiRecallEnabled = aiEnabled,
                recallMode = recallMode,
                widgetContentTypes = contentTypes,
            )
        }

    override suspend fun updateSettings(settings: MemoriesSettings) {
        Napier.i("Updating memories settings: $settings")
        keyValueStorage.putBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, settings.contextualRecommendationsEnabled)
        keyValueStorage.putBoolean(KEY_AI_RECALL_ENABLED, settings.aiRecallEnabled)
        keyValueStorage.putString(KEY_RECALL_MODE, settings.recallMode.name)
        keyValueStorage.putString(KEY_WIDGET_CONTENT_TYPES, settings.widgetContentTypes.joinToString(",") { it.name })
    }

    override suspend fun setContextualRecommendationsEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, enabled)
    }

    override suspend fun setAiRecallEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_AI_RECALL_ENABLED, enabled)
    }

    override suspend fun setRecallMode(mode: RecallMode) {
        keyValueStorage.putString(KEY_RECALL_MODE, mode.name)
    }

    override suspend fun setWidgetContentTypes(types: Set<WidgetContentType>) {
        keyValueStorage.putString(KEY_WIDGET_CONTENT_TYPES, types.joinToString(",") { it.name })
    }

    private suspend fun loadRecallMode(): RecallMode {
        val stored = keyValueStorage.getString(KEY_RECALL_MODE) ?: return RecallMode.ON_THIS_DAY
        return parseRecallMode(stored)
    }

    private suspend fun loadWidgetContentTypes(): Set<WidgetContentType> {
        val stored = keyValueStorage.getString(KEY_WIDGET_CONTENT_TYPES) ?: return WidgetContentType.ALL
        return parseContentTypes(stored)
    }
}

private fun parseRecallMode(stored: String?): RecallMode {
    if (stored == null) return RecallMode.ON_THIS_DAY
    return runCatching { RecallMode.valueOf(stored) }.getOrDefault(RecallMode.ON_THIS_DAY)
}

private fun parseContentTypes(stored: String?): Set<WidgetContentType> {
    if (stored == null) return WidgetContentType.ALL
    return stored
        .split(",")
        .mapNotNull { name -> runCatching { WidgetContentType.valueOf(name.trim()) }.getOrNull() }
        .toSet()
        .ifEmpty { WidgetContentType.ALL }
}
