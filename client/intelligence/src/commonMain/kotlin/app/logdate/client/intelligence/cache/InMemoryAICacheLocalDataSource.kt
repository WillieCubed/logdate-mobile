package app.logdate.client.intelligence.cache

object InMemoryAICacheLocalDataSource : AICacheLocalDataSource {
    private val cache = mutableMapOf<String, GenerativeAICacheEntry>()

    override fun get(key: String): GenerativeAICacheEntry? = cache[key]

    override fun set(key: String, entry: GenerativeAICacheEntry) {
        cache[key] = entry
    }

    override fun remove(key: String) {
        cache.remove(key)
    }

    override fun entries(): List<GenerativeAICacheEntry> = cache.values.toList()

    override fun clear() {
        cache.clear()
    }
}
