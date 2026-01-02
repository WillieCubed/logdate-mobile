package app.logdate.client.intelligence.cache

class IOSAICacheLocalDataSource : AICacheLocalDataSource {
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
