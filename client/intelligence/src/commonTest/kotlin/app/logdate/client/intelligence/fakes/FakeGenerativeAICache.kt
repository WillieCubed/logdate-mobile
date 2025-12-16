package app.logdate.client.intelligence.fakes

import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheEntry
import kotlinx.datetime.Clock

/**
 * Fake implementation of GenerativeAICache for testing.
 */
class FakeGenerativeAICache : GenerativeAICache {
    private val entries = mutableMapOf<String, GenerativeAICacheEntry>()
    
    var getEntryCalls = mutableListOf<String>()
    var putEntryCalls = mutableListOf<Pair<String, String>>()
    var purgeCalls = 0
    
    override suspend fun getEntry(key: String): GenerativeAICacheEntry? {
        getEntryCalls.add(key)
        return entries[key]
    }
    
    override suspend fun putEntry(key: String, value: String) {
        putEntryCalls.add(key to value)
        entries[key] = GenerativeAICacheEntry(
            key = key,
            content = value,
            lastUpdated = Clock.System.now()
        )
    }
    
    override suspend fun purge() {
        purgeCalls++
        entries.clear()
    }
    
    fun clear() {
        entries.clear()
        getEntryCalls.clear()
        putEntryCalls.clear()
        purgeCalls = 0
    }
    
    fun hasEntry(key: String): Boolean = entries.containsKey(key)
    
    fun setEntry(key: String, content: String) {
        entries[key] = GenerativeAICacheEntry(
            key = key,
            content = content,
            lastUpdated = Clock.System.now()
        )
    }
}