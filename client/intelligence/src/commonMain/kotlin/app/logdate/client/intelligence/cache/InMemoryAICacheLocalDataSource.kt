package app.logdate.client.intelligence.cache

object InMemoryAICacheLocalDataSource : AICacheLocalDataSource {
    private val cache = mutableMapOf<String, GenerativeAICacheEntry>()

    override fun get(key: String): GenerativeAICacheEntry? {
        return cache[key]
    }

    override fun set(key: String, summary: GenerativeAICacheEntry) {
        cache[key] = summary
    }

    override fun clear() {
        cache.clear()
    }
}
