package app.logdate.client.intelligence.generativeai

sealed interface GenerativeAIResponseFormat {
    data object Text : GenerativeAIResponseFormat

    data class JsonSchema(
        val name: String,
        val schema: String,
        val strict: Boolean = true,
    ) : GenerativeAIResponseFormat
}
