package app.logdate.client.intelligence.cache

/**
 * A local cache that stores generative AI summaries.
 */
interface AICacheLocalDataSource {

    /**
     * Retrieves a generative AI summary from the cache.
     */
    operator fun get(key: String): GenerativeAICacheEntry?

    /**
     * Stores a generative AI summary in the cache.
     */
    operator fun set(key: String, summary: GenerativeAICacheEntry)

    /**
     * Clears the cache of all generative AI entries.
     */
    fun clear()
}
