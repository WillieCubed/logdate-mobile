package app.logdate.client.intelligence.cache

import kotlinx.datetime.Instant
import java.io.File

class AndroidAICacheLocalDataSource(
    private val cacheDir: String,
) : AICacheLocalDataSource {
    private companion object {
        private const val CACHE_DIR_NAME = "ai_cache"
        private const val FILE_PREFIX = "generated_"
        private const val FILE_SUFFIX = ".txt"
    }

    override operator fun get(key: String): GenerativeAICacheEntry? {
        val file = File(getGenerativeCacheDir(), getRealFilename(key))
        if (!file.exists()) {
            return null
        }
        return GenerativeAICacheEntry(
            key = key,
            content = file.readText(),
            lastUpdated = Instant.fromEpochSeconds(file.lastModified() / 1000),
        )
    }

    override operator fun set(key: String, summary: GenerativeAICacheEntry) {
        val file = File(getGenerativeCacheDir(), getRealFilename(key))
        file.writeText(summary.content)
        file.setLastModified(summary.lastUpdated.epochSeconds * 1000)
    }

    /**
     * Clears the cache of all entries.
     */
    override fun clear() {
        getGenerativeCacheDir().listFiles()?.forEach { it.delete() }
    }

    private fun getGenerativeCacheDir(): File {
        val cacheDir = File(cacheDir, CACHE_DIR_NAME)
        cacheDir.mkdirs()
        return cacheDir
    }

    private fun getRealFilename(key: String) = "$FILE_PREFIX$key$FILE_SUFFIX"
}