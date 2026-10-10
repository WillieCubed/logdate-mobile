package app.logdate.client.media

import app.logdate.client.media.storage.MediaFileResolver
import io.github.aakira.napier.Napier
import java.io.File

/**
 * Android filesystem-backed [MediaCleaner].
 *
 * Accepts `logdate-media://` references, `file:` URIs and absolute paths. Other URI
 * schemes (e.g. `content://`) are no-ops because they refer to assets owned by the
 * system media store, which the editor must not delete.
 */
class AndroidMediaCleaner(
    private val mediaFiles: MediaFileResolver,
) : MediaCleaner {
    override suspend fun delete(path: String) {
        val absolutePath = mediaFiles.filePath(path)
        if (absolutePath == null) {
            Napier.d("MediaCleaner: ignoring non-filesystem path: $path")
            return
        }
        try {
            val file = File(absolutePath)
            if (file.exists() && !file.delete()) {
                Napier.w("MediaCleaner: failed to delete $absolutePath")
            }
        } catch (e: SecurityException) {
            Napier.w("MediaCleaner: security exception deleting $absolutePath: ${e.message}")
        }
    }
}
