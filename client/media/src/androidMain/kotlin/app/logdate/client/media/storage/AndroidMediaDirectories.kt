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

private val resolvers = mutableMapOf<String, MediaFileResolver>()

/**
 * The one [MediaFileResolver] for the app storage of [context], shared by dependency injection and
 * by entry points that cannot inject one (widgets, services), so every reader sees the same
 * directory mapping.
 */
fun androidMediaFileResolver(context: Context): MediaFileResolver =
    synchronized(resolvers) {
        resolvers.getOrPut(context.filesDir.path) { MediaFileResolver(AndroidMediaDirectories(context.filesDir)) }
    }
