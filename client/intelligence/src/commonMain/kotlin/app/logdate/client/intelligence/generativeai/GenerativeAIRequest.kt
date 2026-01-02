package app.logdate.client.intelligence.generativeai

data class GenerativeAIRequest(
    val messages: List<GenerativeAIChatMessage>,
    val model: String? = null,
    val temperature: Double? = null,
)
