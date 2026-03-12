package app.logdate.client.domain.recommendation

/**
 * User preferences for how memories and recommendations behave.
 */
data class MemoriesSettings(
    val contextualRecommendationsEnabled: Boolean = true,
    val aiRecallEnabled: Boolean = false,
)
