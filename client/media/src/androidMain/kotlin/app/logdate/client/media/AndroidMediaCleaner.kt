package app.logdate.client.media

import android.net.Uri
import io.github.aakira.napier.Napier
import java.io.File

/**
 * Android filesystem-backed [MediaCleaner].
 *
 * Accepts both absolute paths and `file://` URIs. Other URI schemes (e.g.
 * `content://`) are no-ops because they refer to assets owned by the system
 * media store, which the editor must not delete.
 */
class AndroidMediaCleaner : MediaCleaner {
    override suspend fun delete(path: String) {
        val absolutePath = path.toFilesystemPath()
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

    private fun String.toFilesystemPath(): String? =
        when {
            startsWith("/") -> this
            startsWith("file:") -> Uri.parse(this).path
            else -> null
        }
}
