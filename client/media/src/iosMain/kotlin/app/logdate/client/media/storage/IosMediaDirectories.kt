@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media.storage

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Where iOS keeps LogDate's media collections, inside the app's container.
 *
 * - [MediaCollection.Library]: `Documents/media`
 * - [MediaCollection.Recordings]: `Library/Application Support/audio_notes`
 *
 * iOS moves the whole container (`…/Containers/Data/Application/<UUID>`) when a backup is restored
 * to another device, when the app is reinstalled, and after some system updates, so a path from
 * any earlier container maps to the same place under the current one.
 */
class IosMediaDirectories internal constructor(
    containerHome: String,
    documentsDirectory: String,
    applicationSupportDirectory: String,
) : MediaDirectories {
    constructor() : this(
        containerHome = NSHomeDirectory(),
        documentsDirectory = systemDirectory(NSDocumentDirectory),
        applicationSupportDirectory = systemDirectory(NSApplicationSupportDirectory),
    )

    private val containerHome = canonical(containerHome)
    private val library = "${canonical(documentsDirectory)}/media"
    private val recordings = "${canonical(applicationSupportDirectory)}/audio_notes"

    override fun directory(collection: MediaCollection): String =
        when (collection) {
            MediaCollection.Library -> library
            MediaCollection.Recordings -> recordings
        }

    override fun canonicalPath(path: String): String = canonical(path)

    override fun pathInCurrentInstall(path: String): String? {
        val canonicalPath = canonical(path)
        val containerStart = canonicalPath.indexOf(APP_CONTAINERS)
        if (containerStart < 0) return null
        val idStart = containerStart + APP_CONTAINERS.length
        val idEnd = canonicalPath.indexOf('/', idStart)
        if (idEnd <= idStart || idEnd == canonicalPath.lastIndex) return null
        return "$containerHome/${canonicalPath.substring(idEnd + 1)}"
    }

    private companion object {
        const val APP_CONTAINERS = "/Containers/Data/Application/"
    }
}

private val sharedResolver by lazy { MediaFileResolver(IosMediaDirectories()) }

/** The one [MediaFileResolver] for this app's iOS container, shared by dependency injection and default arguments. */
fun iosMediaFileResolver(): MediaFileResolver = sharedResolver

/** `/private/var/…` and `/var/…` are the same directory on iOS; LogDate spells it without `/private`. */
private fun canonical(path: String): String = (if (path.startsWith("/private/var/")) path.removePrefix("/private") else path).trimEnd('/')

private fun systemDirectory(directory: NSSearchPathDirectory): String {
    val url: NSURL? =
        NSFileManager.defaultManager.URLForDirectory(
            directory = directory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(url?.path) { "iOS did not provide directory $directory" }
}
