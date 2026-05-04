@file:OptIn(ExperimentalForeignApi::class)

package app.logdate.client.media

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * iOS filesystem-backed [MediaCleaner].
 *
 * Accepts absolute filesystem paths and `file://` URIs. Other schemes are ignored — they
 * refer to assets the editor doesn't own (Photos library entries, remote URLs, etc.).
 */
class IosMediaCleaner : MediaCleaner {
    private val fileManager = NSFileManager.defaultManager

    override suspend fun delete(path: String) {
        val absolutePath = path.toFilesystemPath() ?: run {
            Napier.d("MediaCleaner: ignoring non-filesystem path: $path")
            return
        }
        if (!fileManager.fileExistsAtPath(absolutePath)) return
        val ok = fileManager.removeItemAtPath(absolutePath, error = null)
        if (!ok) {
            Napier.w("MediaCleaner: failed to delete $absolutePath")
        }
    }

    private fun String.toFilesystemPath(): String? =
        when {
            startsWith("/") -> this
            startsWith("file:") -> NSURL.URLWithString(this)?.path
            else -> null
        }
}
