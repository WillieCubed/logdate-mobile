package app.logdate.client.intelligence.cache

/**
 * A cache that stores generative AI summaries in files.
 *
 * Each file is named after the key of the entry, and the content is stored in the file.
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