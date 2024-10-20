package app.logdate.core.data.timeline.cache

import app.logdate.core.coroutines.AppDispatcher
import app.logdate.core.coroutines.Dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import javax.inject.Inject

/**
 * A generative AI cache that stores entries using the device's local persistent storage.
 */
class OfflineGenerativeAICache @Inject constructor(
    private val dataSource: AICacheLocalDataSource,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
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