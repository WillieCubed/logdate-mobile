package app.logdate.client.intelligence.cache

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * A generative AI cache that stores entries using the device's local persistent storage.
 */
class OfflineGenerativeAICache(
    private val dataSource: AICacheLocalDataSource,
    private val keyStrategy: AICacheKeyStrategy = DefaultAICacheKeyStrategy(),
    private val config: AICacheConfig = AICacheConfig(),
    private val clock: Clock = Clock.System,
    private val memoryStore: AICacheMemoryStore = LruAICacheMemoryStore(
        maxEntries = config.memoryMaxEntries,
        maxBytes = config.memoryMaxBytes
    ),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GenerativeAICache {

    override suspend fun getEntry(request: GenerativeAICacheRequest): GenerativeAICacheEntry? {
        return withContext(ioDispatcher) {
            val cacheKey = keyStrategy.createKey(request.toKeyInput())
            val memoryHit = memoryStore.get(cacheKey.value)
            if (memoryHit != null && isEntryValid(memoryHit, cacheKey, request)) {
                return@withContext memoryHit
            }
            if (memoryHit != null) {
                memoryStore.remove(cacheKey.value)
            }
            val entry = runCatching { dataSource.get(cacheKey.value) }
                .onFailure { error ->
                    Napier.w(message = "Failed to read AI cache entry ${cacheKey.value}", throwable = error)
                }
                .getOrNull()
            if (entry == null) {
                return@withContext null
            }
            if (!isEntryValid(entry, cacheKey, request)) {
                removeEntry(cacheKey.value)
                return@withContext null
            }
            memoryStore.put(cacheKey.value, entry)
            entry
        }
    }

    override suspend fun putEntry(request: GenerativeAICacheRequest, content: String) = withContext(ioDispatcher) {
        val cacheKey = keyStrategy.createKey(request.toKeyInput())
        val now = clock.now()
        val expiresAt = now + request.policy.ttlSeconds.seconds
        val metadata = GenerativeAICacheEntryMetadata(
            contentTypeId = request.contentType.id,
            providerId = request.providerId,
            model = request.model,
            promptVersion = request.promptVersion,
            schemaVersion = request.schemaVersion,
            templateId = request.templateId,
            ttlSeconds = request.policy.ttlSeconds,
            expiresAt = expiresAt,
            sourceHash = cacheKey.sourceHash,
            debugPrefix = cacheKey.debugPrefix,
            contentBytes = content.encodeToByteArray().size.toLong(),
        )
        val entry = GenerativeAICacheEntry(
            key = cacheKey.value,
            content = content,
            lastUpdated = now,
            metadata = metadata
        )
        memoryStore.put(cacheKey.value, entry)
        runCatching { dataSource.set(cacheKey.value, entry) }
            .onFailure { error ->
                Napier.w(message = "Failed to write AI cache entry ${cacheKey.value}", throwable = error)
            }
        enforcePersistentLimits()
    }

    override suspend fun purge() = withContext(ioDispatcher) {
        memoryStore.clear()
        runCatching { dataSource.clear() }
            .onFailure { error ->
                Napier.w(message = "Failed to purge AI cache", throwable = error)
            }
        Unit
    }

    private fun isEntryValid(
        entry: GenerativeAICacheEntry,
        key: AICacheKey,
        request: GenerativeAICacheRequest
    ): Boolean {
        if (entry.metadata.expiresAt <= clock.now()) {
            return false
        }
        if (entry.metadata.sourceHash != key.sourceHash) {
            return false
        }
        if (entry.metadata.contentTypeId != request.contentType.id) {
            return false
        }
        if (request.policy.includeProviderInKey && entry.metadata.providerId != request.providerId) {
            return false
        }
        if (request.policy.includeModelInKey && entry.metadata.model != request.model) {
            return false
        }
        if (request.policy.includePromptVersionInKey && entry.metadata.promptVersion != request.promptVersion) {
            return false
        }
        if (request.policy.includeSchemaVersionInKey && entry.metadata.schemaVersion != request.schemaVersion) {
            return false
        }
        if (request.policy.includeTemplateIdInKey && entry.metadata.templateId != request.templateId) {
            return false
        }
        return true
    }

    private fun removeEntry(key: String) {
        memoryStore.remove(key)
        runCatching { dataSource.remove(key) }
            .onFailure { error ->
                Napier.w(message = "Failed to remove AI cache entry $key", throwable = error)
            }
    }

    private fun enforcePersistentLimits() {
        val entries = runCatching { dataSource.entries() }
            .onFailure { error ->
                Napier.w(message = "Failed to list AI cache entries", throwable = error)
            }
            .getOrDefault(emptyList())
        if (entries.isEmpty()) {
            return
        }

        val now = clock.now()
        val mutableEntries = entries.toMutableList()
        val expired = mutableEntries.filter { it.metadata.expiresAt <= now }
        if (expired.isNotEmpty()) {
            expired.forEach { removeEntry(it.key) }
            mutableEntries.removeAll(expired.toSet())
        }

        var totalBytes = mutableEntries.sumOf { it.metadata.contentBytes }
        if (mutableEntries.size <= config.persistentMaxEntries && totalBytes <= config.persistentMaxBytes) {
            return
        }

        val sortedByAge = mutableEntries.sortedBy { it.lastUpdated }
        for (entry in sortedByAge) {
            if (mutableEntries.size <= config.persistentMaxEntries && totalBytes <= config.persistentMaxBytes) {
                break
            }
            removeEntry(entry.key)
            mutableEntries.remove(entry)
            totalBytes -= entry.metadata.contentBytes
        }
    }

    private fun GenerativeAICacheRequest.toKeyInput(): AICacheKeyInput = AICacheKeyInput(
        contentType = contentType,
        inputText = inputText,
        providerId = providerId,
        model = model,
        promptVersion = promptVersion,
        schemaVersion = schemaVersion,
        templateId = templateId,
        policy = policy
    )
}
