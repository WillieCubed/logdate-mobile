package app.logdate.client.intelligence.cache

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * A generative AI cache that stores entries using the device's local persistent storage.
 */
class OfflineGenerativeAICache(
    private val dataSource: AICacheLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : GenerativeAICache {

    override suspend fun getEntry(key: String): GenerativeAICacheEntry? {
        return withContext(ioDispatcher) {
            dataSource[key]
        }
    }

    override suspend fun putEntry(key: String, value: String) = withContext(ioDispatcher) {
        dataSource[key] = GenerativeAICacheEntry(key, value, Clock.System.now())
    }

    override suspend fun purge() = withContext(ioDispatcher) {
        dataSource.clear()
    }
}