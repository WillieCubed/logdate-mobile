package app.logdate.core.data.timeline.cache

import kotlinx.datetime.Instant
import java.io.File
import javax.inject.Inject

/**
 * A cache that stores generative AI summaries in files.
 *
 * Each file is named after the key of the entry, and the content is stored in the file.
 */
class AICacheLocalDataSource @Inject constructor(
    private val cacheDir: File,
) {
    private companion object {
        private const val CACHE_DIR_NAME = "ai_cache"
        private const val FILE_PREFIX = "generated_"
        private const val FILE_SUFFIX = ".txt"
    }

    operator fun get(key: String): GenerativeAICacheEntry? {
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

    operator fun set(key: String, summary: GenerativeAICacheEntry) {
        val file = File(cacheDir, key)
        file.writeText(summary.content)
        file.setLastModified(summary.lastUpdated.epochSeconds * 1000)
    }

    /**
     * Clears the cache of all entries.
     */
    fun clear() {
        getGenerativeCacheDir().listFiles()?.forEach { it.delete() }
    }

    private fun getGenerativeCacheDir(): File {
        val cacheDir = File(cacheDir, CACHE_DIR_NAME)
        cacheDir.mkdirs()
        return cacheDir
    }

    private fun getRealFilename(key: String) = "$FILE_PREFIX$key$FILE_SUFFIX"
}