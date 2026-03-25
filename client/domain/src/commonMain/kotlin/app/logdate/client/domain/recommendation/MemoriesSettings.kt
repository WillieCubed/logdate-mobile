package app.logdate.client.domain.recommendation

/**
 * User preferences for how memories and recommendations behave.
 */
data class MemoriesSettings(
    val contextualRecommendationsEnabled: Boolean = true,
    val aiRecallEnabled: Boolean = false,
    val recallMode: RecallMode = RecallMode.ON_THIS_DAY,
    val widgetContentTypes: Set<WidgetContentType> = WidgetContentType.ALL,
)

/**
 * Strategy for how the widget selects past entries to surface.
 */
enum class RecallMode {
    /** Date-based: entries near today's date from prior years. */
    ON_THIS_DAY,

    /** Broader: surfaces interesting past entries regardless of date. */
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
