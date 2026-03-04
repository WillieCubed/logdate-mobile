@file:OptIn(ExperimentalResourceApi::class)

package app.logdate.screenshots.common

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.ResourceReader
import java.io.File

/**
 * A [ResourceReader] that reads Compose Multiplatform resource files (.cvr) directly
 * from the project's merged debug assets directory on the filesystem.
 *
 * The standard Android [ResourceReader] uses the AssetManager which doesn't properly
 * include compose resources from transitive KMP dependencies in the screenshot test
 * build variant. This reader bypasses that limitation by reading files directly.
 */
internal object ScreenshotResourceReader : ResourceReader {
    private val assetsDir: File by lazy { findAssetsDir() }

    override suspend fun read(path: String): ByteArray {
        val file = assetsDir.resolve(path)
        if (file.exists()) return file.readBytes()
        throw MissingResourceException(path)
    }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
        val file = assetsDir.resolve(path)
        if (!file.exists()) throw MissingResourceException(path)
        return file.inputStream().use { input ->
            var skipped = 0L
            while (skipped < offset) {
                val n = input.skip(offset - skipped)
                if (n == 0L) break
                skipped += n
            }
            val result = ByteArray(size.toInt())
            var read = 0
            while (read < size.toInt()) {
                val n = input.read(result, read, size.toInt() - read)
                if (n <= 0) break
                read += n
            }
            result
        }
    }

    override fun getUri(path: String): String {
        val file = assetsDir.resolve(path)
        if (file.exists()) return file.toURI().toString()
        throw MissingResourceException(path)
    }

    private fun findAssetsDir(): File {
        // Find the repository root by searching for settings.gradle.kts
        val startDir = File(System.getProperty("user.dir"))
        var dir: File? = startDir
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) {
                val assets = File(
                    dir,
                    "app/android-main/build/intermediates/assets/debug/mergeDebugAssets",
                )
                if (assets.exists()) return assets
            }
            dir = dir.parentFile
        }
        // Fallback: return the start directory (will fail gracefully on read)
        return startDir
    }
}
