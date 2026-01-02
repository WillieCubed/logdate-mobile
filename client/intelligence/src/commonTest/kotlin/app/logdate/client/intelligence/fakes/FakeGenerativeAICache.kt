package app.logdate.client.intelligence.fakes

import app.logdate.client.intelligence.cache.AICacheKeyInput
import app.logdate.client.intelligence.cache.AICacheKeyStrategy
import app.logdate.client.intelligence.cache.DefaultAICacheKeyStrategy
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.cache.GenerativeAICacheEntry
import app.logdate.client.intelligence.cache.GenerativeAICacheEntryMetadata
import app.logdate.client.intelligence.cache.GenerativeAICacheRequest
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Fake implementation of GenerativeAICache for testing.
 */
class FakeGenerativeAICache(
    private val clock: Clock = Clock.System,
    private val keyStrategy: AICacheKeyStrategy = DefaultAICacheKeyStrategy(),
) : GenerativeAICache {
    private val entries = mutableMapOf<String, GenerativeAICacheEntry>()
    
    var getEntryCalls = mutableListOf<GenerativeAICacheRequest>()
    var putEntryCalls = mutableListOf<Pair<GenerativeAICacheRequest, String>>()
    var purgeCalls = 0
    
    override suspend fun getEntry(request: GenerativeAICacheRequest): GenerativeAICacheEntry? {
        getEntryCalls.add(request)
        val key = keyStrategy.createKey(request.toKeyInput())
        return entries[key.value]
    }
    
    override suspend fun putEntry(request: GenerativeAICacheRequest, content: String) {
        putEntryCalls.add(request to content)
        val key = keyStrategy.createKey(request.toKeyInput())
        entries[key.value] = createEntry(key.value, key.sourceHash, key.debugPrefix, request, content)
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
    
    fun hasEntry(request: GenerativeAICacheRequest): Boolean {
        val key = keyStrategy.createKey(request.toKeyInput())
        return entries.containsKey(key.value)
    }
    
    fun setEntry(request: GenerativeAICacheRequest, content: String) {
        val key = keyStrategy.createKey(request.toKeyInput())
        entries[key.value] = createEntry(key.value, key.sourceHash, key.debugPrefix, request, content)
    }

    private fun createEntry(
        key: String,
        sourceHash: String,
        debugPrefix: String,
        request: GenerativeAICacheRequest,
        content: String
    ): GenerativeAICacheEntry {
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
            sourceHash = sourceHash,
            debugPrefix = debugPrefix,
            contentBytes = content.encodeToByteArray().size.toLong(),
        )
        return GenerativeAICacheEntry(
            key = key,
            content = content,
            lastUpdated = now,
            metadata = metadata
        )
    }

    private fun GenerativeAICacheRequest.toKeyInput() = AICacheKeyInput(
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
