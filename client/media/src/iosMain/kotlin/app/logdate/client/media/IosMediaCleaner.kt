@file:OptIn(ExperimentalForeignApi::class)

package app.logdate.client.media

import app.logdate.client.media.storage.MediaFileResolver
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

/**
 * iOS filesystem-backed [MediaCleaner].
 *
 * Accepts `logdate-media://` references, `file:` URIs and absolute paths, including paths from an
 * earlier app container. Other schemes are ignored — they refer to assets the editor doesn't own
 * (Photos library entries, remote URLs, etc.).
 */
class IosMediaCleaner(
    private val mediaFiles: MediaFileResolver,
) : MediaCleaner {
    private val fileManager = NSFileManager.defaultManager

    override suspend fun delete(path: String) {
        val absolutePath =
            mediaFiles.filePath(path) ?: run {
                Napier.d("MediaCleaner: ignoring non-filesystem path: $path")
                return
            }
        if (!fileManager.fileExistsAtPath(absolutePath)) return
        val ok = fileManager.removeItemAtPath(absolutePath, error = null)
        if (!ok) {
            Napier.w("MediaCleaner: failed to delete $absolutePath")
        }
    }
}
