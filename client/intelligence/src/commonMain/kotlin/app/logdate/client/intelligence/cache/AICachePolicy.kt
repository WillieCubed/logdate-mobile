package app.logdate.client.intelligence.cache

data class AICachePolicy(
    val ttlSeconds: Long,
    val includeProviderInKey: Boolean = true,
    val includeModelInKey: Boolean = true,
    val includePromptVersionInKey: Boolean = true,
    val includeSchemaVersionInKey: Boolean = true,
    val includeTemplateIdInKey: Boolean = true,
)
