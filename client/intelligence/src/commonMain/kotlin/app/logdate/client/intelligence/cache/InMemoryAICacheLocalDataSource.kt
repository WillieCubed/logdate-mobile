package app.logdate.client.intelligence.cache

import kotlinx.datetime.Clock

object InMemoryAICacheLocalDataSource : AICacheLocalDataSource {
    private val cache = mutableMapOf<String, String>()

    override fun get(key: String): GenerativeAICacheEntry {
        return GenerativeAICacheEntry(key, cache[key] ?: "", Clock.System.now())
    }
    override fun set(key: String, summary: GenerativeAICacheEntry) {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }
}