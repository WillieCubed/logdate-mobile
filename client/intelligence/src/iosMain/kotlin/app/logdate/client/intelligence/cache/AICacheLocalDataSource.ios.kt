package app.logdate.client.intelligence.cache

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile
import platform.Foundation.NSData

private const val CACHE_DIR_NAME = "ai_cache"

class IOSAICacheLocalDataSource(
    private val cacheRootPath: String = defaultCachePath(),
    private val codec: AICacheEntryCodec = JsonAICacheEntryCodec(),
) : AICacheLocalDataSource {
    private companion object {
        private const val FILE_PREFIX = "generated_"
        private const val FILE_SUFFIX = ".json"
        private val unsafeChars = Regex("[^A-Za-z0-9._-]")
    }

    override fun get(key: String): GenerativeAICacheEntry? {
        val filePath = filePathForKey(key)
        val data = NSData.dataWithContentsOfFile(filePath) ?: return null
        val raw = NSString.create(data = data, encoding = NSUTF8StringEncoding) ?: return null
        return runCatching { codec.decode(raw.toString()) }
            .onFailure { error ->
                Napier.w(message = "Failed to decode AI cache entry for $key", throwable = error)
                NSFileManager.defaultManager.removeItemAtPath(filePath, error = null)
            }
            .getOrNull()
    }

    override fun set(key: String, entry: GenerativeAICacheEntry) {
        ensureCacheDir()
        val encoded = codec.encode(entry)
        val nsString = NSString.create(string = encoded)
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return
        runCatching {
            data.writeToFile(filePathForKey(key), true)
        }.onFailure { error ->
            Napier.w(message = "Failed to write AI cache entry for $key", throwable = error)
        }
    }

    override fun remove(key: String) {
        runCatching {
            NSFileManager.defaultManager.removeItemAtPath(filePathForKey(key), error = null)
        }.onFailure { error ->
            Napier.w(message = "Failed to remove AI cache entry for $key", throwable = error)
        }
    }

    override fun entries(): List<GenerativeAICacheEntry> {
        val fileManager = NSFileManager.defaultManager
        val files = fileManager.contentsOfDirectoryAtPath(cacheRootPath, error = null)
            ?: return emptyList()
        return files
            .filterIsInstance<String>()
            .filter { it.endsWith(FILE_SUFFIX) }
            .mapNotNull { filename ->
                val data = NSData.dataWithContentsOfFile("$cacheRootPath/$filename") ?: return@mapNotNull null
                val raw = NSString.create(data = data, encoding = NSUTF8StringEncoding) ?: return@mapNotNull null
                runCatching { codec.decode(raw.toString()) }
                    .onFailure { error ->
                        Napier.w(
                            message = "Failed to decode AI cache entry from $filename",
                            throwable = error
                        )
                        NSFileManager.defaultManager.removeItemAtPath(
                            "$cacheRootPath/$filename",
                            error = null
                        )
                    }
                    .getOrNull()
            }
    }

    override fun clear() {
        val fileManager = NSFileManager.defaultManager
        val files = fileManager.contentsOfDirectoryAtPath(cacheRootPath, error = null) ?: return
        files.filterIsInstance<String>()
            .filter { it.endsWith(FILE_SUFFIX) }
            .forEach { fileName ->
                fileManager.removeItemAtPath("$cacheRootPath/$fileName", error = null)
            }
    }
    }

    private fun filePathForKey(key: String): String {
        return "$cacheRootPath/$FILE_PREFIX${sanitize(key)}$FILE_SUFFIX"
    }

    private fun ensureCacheDir() {
        val fileManager = NSFileManager.defaultManager
        fileManager.createDirectoryAtPath(
            cacheRootPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    private fun sanitize(key: String): String = key.replace(unsafeChars, "_")
}

@OptIn(ExperimentalForeignApi::class)
private fun defaultCachePath(): String {
    val fileManager = NSFileManager.defaultManager
    val url: NSURL? = fileManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    val basePath = requireNotNull(url?.path)
    return "$basePath/$CACHE_DIR_NAME"
}
