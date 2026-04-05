package app.logdate.client.domain.recommendation

/**
 * User preferences for how memories and recommendations behave.
 */
data class MemoriesSettings(
    val contextualRecommendationsEnabled: Boolean = true,
    val ambientPromptsEnabled: Boolean = true,
    val captureNudgesEnabled: Boolean = true,
    val draftRescueEnabled: Boolean = true,
    val memoryRecallNotificationsEnabled: Boolean = true,
    val morningPromptEnabled: Boolean = true,
    val eveningPromptEnabled: Boolean = true,
    val morningPromptTime: AmbientPromptTime = AmbientPromptTime(hour = 8, minute = 0),
    val eveningPromptTime: AmbientPromptTime = AmbientPromptTime(hour = 21, minute = 0),
    val aiRecallEnabled: Boolean = false,
    val recallMode: RecallMode = RecallMode.ON_THIS_DAY,
    val widgetContentTypes: Set<WidgetContentType> = WidgetContentType.ALL,
)

/**
 * User-selected local delivery time for ambient prompts.
 */
data class AmbientPromptTime(
    val hour: Int,
    val minute: Int,
) {
    init {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
    }

    fun toStorageString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        fun parseOrNull(value: String?): AmbientPromptTime? {
            if (value.isNullOrBlank()) return null
            val parts = value.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            return runCatching { AmbientPromptTime(hour = hour, minute = minute) }.getOrNull()
        }
    }
}

/**
 * Strategy for how the widget selects past entries to surface.
 */
enum class RecallMode {
    /** Date-based: entries near today's date from prior years. */
    ON_THIS_DAY,

    /** Archive-based: surfaces notable older entries regardless of date. */
    REDISCOVER,
}

/**
 * Types of content the widget can display.
 */
enum class WidgetContentType {
    TEXT,
    PHOTOS,
    AUDIO,
    ;

    companion object {
        val ALL: Set<WidgetContentType> = entries.toSet()
    }
}
