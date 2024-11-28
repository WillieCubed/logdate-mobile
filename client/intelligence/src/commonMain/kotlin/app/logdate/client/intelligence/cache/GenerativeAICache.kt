package app.logdate.client.intelligence.cache

import kotlinx.datetime.Instant

/**
 * A cache for storing generative AI summaries.
 */
interface GenerativeAICache {

    /**
     * Retrieves a generative AI summary from the cache.
     */
    suspend fun getEntry(key: String): GenerativeAICacheEntry?

    /**
     * Stores a generative AI summary in the cache.
     */
    suspend fun putEntry(key: String, value: String)

    /**
     * Purges the cache of all generative AI entries.
     */
    suspend fun purge()
}

/**
 * A cache entry for a generative AI summary.
 */
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
)
