package app.logdate.client.intelligence.cache

import kotlinx.datetime.Instant
import java.io.File

class JvmAICacheLocalDataSource(
    private val cacheRoot: File = File(System.getProperty("java.io.tmpdir"), "logdate_ai_cache")
) : AICacheLocalDataSource {
    private companion object {
        private const val FILE_PREFIX = "generated_"
        private const val FILE_SUFFIX = ".txt"
    }

    override fun get(key: String): GenerativeAICacheEntry? {
        val file = File(cacheRoot, getRealFilename(key))
        if (!file.exists()) {
            return null
        }
        return GenerativeAICacheEntry(
            key = key,
            content = file.readText(),
            lastUpdated = Instant.fromEpochSeconds(file.lastModified() / 1000)
        )
    }

    override fun set(key: String, summary: GenerativeAICacheEntry) {
        cacheRoot.mkdirs()
        val file = File(cacheRoot, getRealFilename(key))
        file.writeText(summary.content)
        file.setLastModified(summary.lastUpdated.epochSeconds * 1000)
    }

    override fun clear() {
        cacheRoot.listFiles()?.forEach { it.delete() }
    }

    private fun getRealFilename(key: String) = "$FILE_PREFIX$key$FILE_SUFFIX"
}
