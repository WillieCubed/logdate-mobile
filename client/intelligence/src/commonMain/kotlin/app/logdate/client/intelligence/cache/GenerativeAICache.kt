package app.logdate.client.intelligence.cache

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A cache for storing generative AI outputs.
 */
interface GenerativeAICache {

    /**
     * Retrieves a generative AI response from the cache.
     */
    suspend fun getEntry(request: GenerativeAICacheRequest): GenerativeAICacheEntry?

    /**
     * Stores a generative AI response in the cache.
     */
    suspend fun putEntry(request: GenerativeAICacheRequest, content: String)

    /**
     * Purges the cache of all generative AI entries.
     */
    suspend fun purge()
}

/**
 * A cache entry for a generative AI response.
 */
@Serializable
data class GenerativeAICacheEntry(
    /**
     * The primary identifier for the cache entry.
     */
    val key: String,
    /**
     * The content of the cache entry.
     */
    val content: String,
    /**
     * The last time the cache entry was updated.
     */
    val lastUpdated: Instant,
    /**
     * Metadata describing the cached content and its validity window.
     */
    val metadata: GenerativeAICacheEntryMetadata,
)

@Serializable
data class GenerativeAICacheEntryMetadata(
    val contentTypeId: String,
    val providerId: String?,
    val model: String?,
    val promptVersion: String,
    val schemaVersion: String,
    val templateId: String?,
    val ttlSeconds: Long,
    val expiresAt: Instant,
    val sourceHash: String,
    val debugPrefix: String,
    val contentBytes: Long,
)
