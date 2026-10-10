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
 * primary user), so a path written for another user, profile or spelling maps to the same place
 * under this install's data directory. Only LogDate's own package names are recognised, never
 * another app's.
 */
class AndroidMediaDirectories(
    filesDir: File,
) : MediaDirectories {
    private val files = filesDir.canonicalFile
    private val dataDir = files.parentFile ?: files
    private val library = File(files, "media").path
    private val recordings = File(files, "audio_notes").path
    private val appDataPath =
        Regex(
            "^/data/(?:data|user/\\d+|user_de/\\d+)/(?:${(KNOWN_PACKAGES + dataDir.name).joinToString(
                "|",
                transform = Regex::escape,
            )})/(.+)$",
        )

    override fun directory(collection: MediaCollection): String =
        when (collection) {
            MediaCollection.Library -> library
            MediaCollection.Recordings -> recordings
        }

    override fun canonicalPath(path: String): String = File(path).canonicalPath

    override fun pathInCurrentInstall(path: String): String? {
        val withinAppData = appDataPath.matchEntire(path)?.groupValues?.get(1) ?: return null
        return File(dataDir, withinAppData).path
    }

    private companion object {
        /** The package names LogDate has shipped under; their data directories hold the same layout. */
        val KNOWN_PACKAGES = listOf("studio.hypertext.logdate", "co.reasonabletech.logdate")
    }
}

/** The [MediaFileResolver] for this app's Android storage, for entry points that cannot inject one. */
fun androidMediaFileResolver(context: Context): MediaFileResolver = MediaFileResolver(AndroidMediaDirectories(context.filesDir))
