package app.logdate.client.intelligence.cache

class IOSAICacheLocalDataSource(
    private val cacheDir: String,
) : AICacheLocalDataSource {

    override fun get(key: String): GenerativeAICacheEntry? {
        TODO("Not yet implemented")
    }

    override fun set(key: String, summary: GenerativeAICacheEntry) {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }
}