package app.logdate.client.intelligence.cache

import io.github.aakira.napier.Napier
import java.io.File

class JvmAICacheLocalDataSource(
    private val cacheRoot: File = File(System.getProperty("java.io.tmpdir"), "logdate_ai_cache"),
    private val codec: AICacheEntryCodec = JsonAICacheEntryCodec(),
) : AICacheLocalDataSource {
    private companion object {
        private const val FILE_PREFIX = "generated_"
        private const val FILE_SUFFIX = ".json"
        private val unsafeChars = Regex("[^A-Za-z0-9._-]")
    }

    override fun get(key: String): GenerativeAICacheEntry? {
        val file = File(cacheRoot, getRealFilename(key))
        if (!file.exists()) {
            return null
        }
        return runCatching {
            codec.decode(file.readText())
        }.onFailure { error ->
            Napier.w(message = "Failed to decode AI cache entry for $key", throwable = error)
            file.delete()
        }.getOrNull()
    }

    override fun set(key: String, entry: GenerativeAICacheEntry) {
        cacheRoot.mkdirs()
        val file = File(cacheRoot, getRealFilename(key))
        runCatching {
            file.writeText(codec.encode(entry))
            file.setLastModified(entry.lastUpdated.epochSeconds * 1000)
        }.onFailure { error ->
            Napier.w(message = "Failed to write AI cache entry for $key", throwable = error)
        }
    }

    override fun remove(key: String) {
        runCatching {
            val file = File(cacheRoot, getRealFilename(key))
            if (file.exists()) {
                file.delete()
            }
        }.onFailure { error ->
            Napier.w(message = "Failed to remove AI cache entry for $key", throwable = error)
        }
    }

    override fun entries(): List<GenerativeAICacheEntry> {
        cacheRoot.mkdirs()
        val files = cacheRoot.listFiles()
            ?.filter { it.isFile && it.name.endsWith(FILE_SUFFIX) }
            .orEmpty()
        if (files.isEmpty()) {
            return emptyList()
        }
        return files.mapNotNull { file ->
            runCatching {
                codec.decode(file.readText())
            }.onFailure { error ->
                Napier.w(message = "Failed to decode AI cache entry from ${file.name}", throwable = error)
                file.delete()
            }.getOrNull()
        }
    }

    override fun clear() {
        cacheRoot.listFiles()?.forEach { it.delete() }
    }

    private fun getRealFilename(key: String) = "$FILE_PREFIX${sanitize(key)}$FILE_SUFFIX"

    private fun sanitize(key: String): String = key.replace(unsafeChars, "_")
}
