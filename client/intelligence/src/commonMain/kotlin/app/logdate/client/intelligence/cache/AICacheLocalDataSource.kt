package app.logdate.client.intelligence.cache

/**
 * A local cache that stores generative AI responses.
 */
interface AICacheLocalDataSource {

    /**
     * Retrieves a generative AI response from the cache.
     */
    fun get(key: String): GenerativeAICacheEntry?

    /**
     * Stores a generative AI response in the cache.
     */
    fun set(key: String, entry: GenerativeAICacheEntry)

    /**
     * Removes a cached entry by key.
     */
    fun remove(key: String)

    /**
     * Lists all cached entries stored locally.
     */
    fun entries(): List<GenerativeAICacheEntry>

    /**
     * Clears the cache of all generative AI entries.
     */
    fun clear()
}
