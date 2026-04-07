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
        private const val KEY_AMBIENT_PROMPTS_ENABLED = "memories_ambient_prompts_enabled"
        private const val KEY_CAPTURE_NUDGES_ENABLED = "memories_capture_nudges_enabled"
        private const val KEY_DRAFT_RESCUE_ENABLED = "memories_draft_rescue_enabled"
        private const val KEY_MEMORY_RECALL_NOTIFICATIONS_ENABLED = "memories_memory_recall_notifications_enabled"
        private const val KEY_EVENT_NUDGES_ENABLED = "memories_event_nudges_enabled"
        private const val KEY_MORNING_PROMPT_ENABLED = "memories_morning_prompt_enabled"
        private const val KEY_EVENING_PROMPT_ENABLED = "memories_evening_prompt_enabled"
        private const val KEY_MORNING_PROMPT_TIME = "memories_morning_prompt_time"
        private const val KEY_EVENING_PROMPT_TIME = "memories_evening_prompt_time"
        private const val KEY_AI_RECALL_ENABLED = "memories_ai_recall_enabled"
        private const val KEY_RECALL_MODE = "memories_recall_mode"
        private const val KEY_WIDGET_CONTENT_TYPES = "memories_widget_content_types"
    }

    override suspend fun getSettings(): MemoriesSettings =
        MemoriesSettings(
            contextualRecommendationsEnabled =
                keyValueStorage.getBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, true),
            ambientPromptsEnabled =
                keyValueStorage.getBoolean(KEY_AMBIENT_PROMPTS_ENABLED, true),
            captureNudgesEnabled =
                keyValueStorage.getBoolean(KEY_CAPTURE_NUDGES_ENABLED, true),
            draftRescueEnabled =
                keyValueStorage.getBoolean(KEY_DRAFT_RESCUE_ENABLED, true),
            memoryRecallNotificationsEnabled =
                keyValueStorage.getBoolean(KEY_MEMORY_RECALL_NOTIFICATIONS_ENABLED, true),
            eventNudgesEnabled =
                keyValueStorage.getBoolean(KEY_EVENT_NUDGES_ENABLED, true),
            morningPromptEnabled =
                keyValueStorage.getBoolean(KEY_MORNING_PROMPT_ENABLED, true),
            eveningPromptEnabled =
                keyValueStorage.getBoolean(KEY_EVENING_PROMPT_ENABLED, true),
            morningPromptTime = loadPromptTime(KEY_MORNING_PROMPT_TIME, defaultValue = AmbientPromptTime(8, 0)),
            eveningPromptTime = loadPromptTime(KEY_EVENING_PROMPT_TIME, defaultValue = AmbientPromptTime(21, 0)),
            aiRecallEnabled =
                keyValueStorage.getBoolean(KEY_AI_RECALL_ENABLED, false),
            recallMode = loadRecallMode(),
            widgetContentTypes = loadWidgetContentTypes(),
        )

    override fun observeSettings(): Flow<MemoriesSettings> =
        combine(
            // Outer combine of two slices because the typed combine() tops out at five flows
            // and ambient settings now has six.
            combine(
                combine(
                    keyValueStorage.observeBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, true),
                    keyValueStorage.observeBoolean(KEY_AMBIENT_PROMPTS_ENABLED, true),
                    keyValueStorage.observeBoolean(KEY_CAPTURE_NUDGES_ENABLED, true),
                    keyValueStorage.observeBoolean(KEY_DRAFT_RESCUE_ENABLED, true),
                    keyValueStorage.observeBoolean(KEY_MEMORY_RECALL_NOTIFICATIONS_ENABLED, true),
                ) {
                    contextualRecommendationsEnabled,
                    ambientPromptsEnabled,
                    captureNudgesEnabled,
                    draftRescueEnabled,
                    memoryRecallNotificationsEnabled,
                    ->
                    AmbientSettingsState(
                        contextualRecommendationsEnabled = contextualRecommendationsEnabled,
                        ambientPromptsEnabled = ambientPromptsEnabled,
                        captureNudgesEnabled = captureNudgesEnabled,
                        draftRescueEnabled = draftRescueEnabled,
                        memoryRecallNotificationsEnabled = memoryRecallNotificationsEnabled,
                        eventNudgesEnabled = true,
                    )
                },
                keyValueStorage.observeBoolean(KEY_EVENT_NUDGES_ENABLED, true),
            ) { base, eventNudgesEnabled ->
                base.copy(eventNudgesEnabled = eventNudgesEnabled)
            },
            combine(
                keyValueStorage.observeBoolean(KEY_MORNING_PROMPT_ENABLED, true),
                keyValueStorage.observeBoolean(KEY_EVENING_PROMPT_ENABLED, true),
                keyValueStorage.observeString(KEY_MORNING_PROMPT_TIME).map {
                    AmbientPromptTime.parseOrNull(it) ?: AmbientPromptTime(8, 0)
                },
                keyValueStorage.observeString(KEY_EVENING_PROMPT_TIME).map {
                    AmbientPromptTime.parseOrNull(it) ?: AmbientPromptTime(21, 0)
                },
            ) { morningPromptEnabled, eveningPromptEnabled, morningPromptTime, eveningPromptTime ->
                PromptScheduleState(
                    morningPromptEnabled = morningPromptEnabled,
                    eveningPromptEnabled = eveningPromptEnabled,
                    morningPromptTime = morningPromptTime,
                    eveningPromptTime = eveningPromptTime,
                )
            },
            combine(
                keyValueStorage.observeBoolean(KEY_AI_RECALL_ENABLED, false),
                keyValueStorage.observeString(KEY_RECALL_MODE).map { parseRecallMode(it) },
                keyValueStorage.observeString(KEY_WIDGET_CONTENT_TYPES).map { parseContentTypes(it) },
            ) { aiRecallEnabled, recallMode, widgetContentTypes ->
                RecallSettingsState(
                    aiRecallEnabled = aiRecallEnabled,
                    recallMode = recallMode,
                    widgetContentTypes = widgetContentTypes,
                )
            },
        ) { ambientSettings, promptSchedule, recallSettings ->
            MemoriesSettings(
                contextualRecommendationsEnabled = ambientSettings.contextualRecommendationsEnabled,
                ambientPromptsEnabled = ambientSettings.ambientPromptsEnabled,
                captureNudgesEnabled = ambientSettings.captureNudgesEnabled,
                draftRescueEnabled = ambientSettings.draftRescueEnabled,
                memoryRecallNotificationsEnabled = ambientSettings.memoryRecallNotificationsEnabled,
                eventNudgesEnabled = ambientSettings.eventNudgesEnabled,
                morningPromptEnabled = promptSchedule.morningPromptEnabled,
                eveningPromptEnabled = promptSchedule.eveningPromptEnabled,
                morningPromptTime = promptSchedule.morningPromptTime,
                eveningPromptTime = promptSchedule.eveningPromptTime,
                aiRecallEnabled = recallSettings.aiRecallEnabled,
                recallMode = recallSettings.recallMode,
                widgetContentTypes = recallSettings.widgetContentTypes,
            )
        }

    override suspend fun updateSettings(settings: MemoriesSettings) {
        Napier.i("Updating memories settings: $settings")
        keyValueStorage.putBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, settings.contextualRecommendationsEnabled)
        keyValueStorage.putBoolean(KEY_AMBIENT_PROMPTS_ENABLED, settings.ambientPromptsEnabled)
        keyValueStorage.putBoolean(KEY_CAPTURE_NUDGES_ENABLED, settings.captureNudgesEnabled)
        keyValueStorage.putBoolean(KEY_DRAFT_RESCUE_ENABLED, settings.draftRescueEnabled)
        keyValueStorage.putBoolean(KEY_MEMORY_RECALL_NOTIFICATIONS_ENABLED, settings.memoryRecallNotificationsEnabled)
        keyValueStorage.putBoolean(KEY_EVENT_NUDGES_ENABLED, settings.eventNudgesEnabled)
        keyValueStorage.putBoolean(KEY_MORNING_PROMPT_ENABLED, settings.morningPromptEnabled)
        keyValueStorage.putBoolean(KEY_EVENING_PROMPT_ENABLED, settings.eveningPromptEnabled)
        keyValueStorage.putString(KEY_MORNING_PROMPT_TIME, settings.morningPromptTime.toStorageString())
        keyValueStorage.putString(KEY_EVENING_PROMPT_TIME, settings.eveningPromptTime.toStorageString())
        keyValueStorage.putBoolean(KEY_AI_RECALL_ENABLED, settings.aiRecallEnabled)
        keyValueStorage.putString(KEY_RECALL_MODE, settings.recallMode.name)
        keyValueStorage.putString(KEY_WIDGET_CONTENT_TYPES, settings.widgetContentTypes.joinToString(",") { it.name })
    }

    override suspend fun setContextualRecommendationsEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_CONTEXTUAL_RECOMMENDATIONS_ENABLED, enabled)
    }

    override suspend fun setAmbientPromptsEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_AMBIENT_PROMPTS_ENABLED, enabled)
    }

    override suspend fun setCaptureNudgesEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_CAPTURE_NUDGES_ENABLED, enabled)
    }

    override suspend fun setDraftRescueEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_DRAFT_RESCUE_ENABLED, enabled)
    }

    override suspend fun setMemoryRecallNotificationsEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_MEMORY_RECALL_NOTIFICATIONS_ENABLED, enabled)
    }

    override suspend fun setEventNudgesEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_EVENT_NUDGES_ENABLED, enabled)
    }

    override suspend fun setMorningPromptEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_MORNING_PROMPT_ENABLED, enabled)
    }

    override suspend fun setEveningPromptEnabled(enabled: Boolean) {
        keyValueStorage.putBoolean(KEY_EVENING_PROMPT_ENABLED, enabled)
    }

    override suspend fun setMorningPromptTime(time: AmbientPromptTime) {
        keyValueStorage.putString(KEY_MORNING_PROMPT_TIME, time.toStorageString())
    }

    override suspend fun setEveningPromptTime(time: AmbientPromptTime) {
        keyValueStorage.putString(KEY_EVENING_PROMPT_TIME, time.toStorageString())
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

    private suspend fun loadPromptTime(
        key: String,
        defaultValue: AmbientPromptTime,
    ): AmbientPromptTime = AmbientPromptTime.parseOrNull(keyValueStorage.getString(key)) ?: defaultValue
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

private data class AmbientSettingsState(
    val contextualRecommendationsEnabled: Boolean,
    val ambientPromptsEnabled: Boolean,
    val captureNudgesEnabled: Boolean,
    val draftRescueEnabled: Boolean,
    val memoryRecallNotificationsEnabled: Boolean,
    val eventNudgesEnabled: Boolean,
)

private data class PromptScheduleState(
    val morningPromptEnabled: Boolean,
    val eveningPromptEnabled: Boolean,
    val morningPromptTime: AmbientPromptTime,
    val eveningPromptTime: AmbientPromptTime,
)

private data class RecallSettingsState(
    val aiRecallEnabled: Boolean,
    val recallMode: RecallMode,
    val widgetContentTypes: Set<WidgetContentType>,
)
