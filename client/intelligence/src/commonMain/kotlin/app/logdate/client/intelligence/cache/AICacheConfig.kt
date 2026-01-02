package app.logdate.client.intelligence.cache

data class AICacheConfig(
    val memoryMaxEntries: Int = 100,
    val memoryMaxBytes: Long = 1_048_576,
    val persistentMaxEntries: Int = 500,
    val persistentMaxBytes: Long = 10_485_760,
)
