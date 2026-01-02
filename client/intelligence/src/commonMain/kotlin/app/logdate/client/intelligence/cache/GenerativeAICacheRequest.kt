package app.logdate.client.intelligence.cache

data class GenerativeAICacheRequest(
    val contentType: GenerativeAICacheContentType,
    val inputText: String,
    val providerId: String?,
    val model: String?,
    val promptVersion: String,
    val schemaVersion: String,
    val templateId: String?,
    val policy: AICachePolicy,
)
