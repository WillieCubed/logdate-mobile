package app.logdate.client.media.storage

import java.io.File

/**
 * Where desktop LogDate keeps its media collections, in the `.logdate` folder of the user's home
 * directory.
 *
 * - [MediaCollection.Library]: `~/.logdate/media`
 * - [MediaCollection.Recordings]: `~/.logdate/audio_notes`
 *
 * Paths use `/` separators on every OS. A path from another home directory (a renamed account or
 * a migrated computer) maps to the same place under this one.
 */
class DesktopMediaDirectories(
    dataRoot: File = File(System.getProperty("user.home"), ".logdate"),
) : MediaDirectories {
    private val root = canonical(dataRoot.path)
    private val library = "$root/media"
    private val recordings = "$root/audio_notes"

    override fun directory(collection: MediaCollection): String =
        when (collection) {
            MediaCollection.Library -> library
            MediaCollection.Recordings -> recordings
        }

    override fun canonicalPath(path: String): String = canonical(path)

    override fun pathInCurrentInstall(path: String): String? {
        val normalized = path.replace('\\', '/')
        val markerStart = normalized.indexOf(DATA_FOLDER)
        if (markerStart < 0) return null
        val withinDataRoot = normalized.substring(markerStart + DATA_FOLDER.length)
        if (withinDataRoot.isEmpty()) return null
        return "$root/$withinDataRoot"
    }

    private companion object {
        const val DATA_FOLDER = "/.logdate/"
    }
}

private val sharedResolver by lazy { MediaFileResolver(DesktopMediaDirectories()) }

/** The one [MediaFileResolver] for this user's LogDate folder, shared by dependency injection and default arguments. */
fun desktopMediaFileResolver(): MediaFileResolver = sharedResolver

private fun canonical(path: String): String = File(path).canonicalPath.replace('\\', '/')
