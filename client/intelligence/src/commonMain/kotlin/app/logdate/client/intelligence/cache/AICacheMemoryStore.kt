package app.logdate.client.intelligence.cache

interface AICacheMemoryStore {
    fun get(key: String): GenerativeAICacheEntry?
    fun put(key: String, entry: GenerativeAICacheEntry)
    fun remove(key: String)
    fun clear()
    fun snapshot(): List<GenerativeAICacheEntry>
}

class LruAICacheMemoryStore(
    private val maxEntries: Int,
    private val maxBytes: Long,
) : AICacheMemoryStore {
    private val entries = LinkedHashMap<String, GenerativeAICacheEntry>(16, 0.75f, true)
    private var currentBytes: Long = 0

    override fun get(key: String): GenerativeAICacheEntry? = entries[key]

    override fun put(key: String, entry: GenerativeAICacheEntry) {
        val existing = entries.remove(key)
        if (existing != null) {
            currentBytes -= existing.metadata.contentBytes
        }
        entries[key] = entry
        currentBytes += entry.metadata.contentBytes
        evictIfNeeded()
    }

    override fun remove(key: String) {
        val removed = entries.remove(key)
        if (removed != null) {
            currentBytes -= removed.metadata.contentBytes
        }
    }

    override fun clear() {
        entries.clear()
        currentBytes = 0
    }

    override fun snapshot(): List<GenerativeAICacheEntry> = entries.values.toList()

    private fun evictIfNeeded() {
        while (entries.size > maxEntries || currentBytes > maxBytes) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) {
                break
            }
            val eldest = iterator.next()
            currentBytes -= eldest.value.metadata.contentBytes
            iterator.remove()
        }
    }
}
