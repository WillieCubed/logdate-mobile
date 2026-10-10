package app.logdate.client.media.storage

import android.content.Context
import java.io.File

/**
 * Where Android (phone and Wear) keeps LogDate's media collections, inside the app's private files.
 *
 * - [MediaCollection.Library]: `filesDir/media` (the content-addressed store lives in `objects/`)
 * - [MediaCollection.Recordings]: `filesDir/audio_notes`
 *
 * App data lives under `/data/user/<user>/<package>` (also spelled `/data/data/<package>` for the
 * primary user), so a path written for another user, profile or package spelling maps to the same
 * place under this install's data directory.
 */
class AndroidMediaDirectories(
    filesDir: File,
) : MediaDirectories {
    private val files = filesDir.canonicalFile
    private val dataDir = files.parentFile ?: files
    private val library = File(files, "media").path
    private val recordings = File(files, "audio_notes").path

    override fun directory(collection: MediaCollection): String =
        when (collection) {
            MediaCollection.Library -> library
            MediaCollection.Recordings -> recordings
        }

    override fun canonicalPath(path: String): String = File(path).canonicalPath

    override fun pathInCurrentInstall(path: String): String? {
        val withinAppData = APP_DATA_PATH.matchEntire(path)?.groupValues?.get(1) ?: return null
        return File(dataDir, withinAppData).path
    }

    private companion object {
        /** `/data/data/<package>/…`, `/data/user/<n>/<package>/…` or `/data/user_de/<n>/<package>/…`. */
        val APP_DATA_PATH = Regex("^/data/(?:data|user/\\d+|user_de/\\d+)/[^/]+/(.+)$")
    }
}

/** The [MediaFileResolver] for this app's Android storage, for entry points that cannot inject one. */
fun androidMediaFileResolver(context: Context): MediaFileResolver = MediaFileResolver(AndroidMediaDirectories(context.filesDir))
