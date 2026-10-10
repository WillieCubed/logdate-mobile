@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media.storage

import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * Moves photos and videos that earlier builds stored outside the media library into it.
 *
 * Earlier builds kept photos picked from Photos in `Library/Caches/photo-library-renderable`, which
 * iOS may empty at any time and does not restore from a backup, and kept camera captures and
 * journal cover images in `Documents/imports`, which no media reference names. While such a file is
 * still there it is moved into the library. A second reference to a file that was already moved
 * gets the same library reference; once iOS has emptied the cache the photo can no longer be found
 * and the entry keeps its old reference.
 */
class IosOutOfLibraryMediaRescuer(
    private val mediaFiles: MediaFileResolver,
) : MediaRescuer {
    override suspend fun rescue(reference: String): String? {
        val path = mediaFiles.filePath(reference) ?: return null
        if (FOLDERS.none { it in path }) return null
        val name = path.substringAfterLast('/')
        val inLibrary = "${mediaFiles.directory(MediaCollection.Library)}/$name"
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(path)) {
            val imported = importFileIntoLibrary(mediaFiles, path, name, move = true) ?: return null
            return mediaFiles.storedReference(imported)
        }
        return mediaFiles.refFor(inLibrary)?.takeIf { fileManager.fileExistsAtPath(inLibrary) }?.toString()
    }

    private companion object {
        val FOLDERS = listOf("/Library/Caches/photo-library-renderable/", "/Documents/imports/")
    }
}

/**
 * Copies, or with [move] moves, the file at [sourcePath] into the media library as [fileName]
 * unless that name is already there, and returns the stored file's URL, or `null` when it could not
 * be stored.
 */
internal fun importFileIntoLibrary(
    mediaFiles: MediaFileResolver,
    sourcePath: String,
    fileName: String,
    move: Boolean = false,
): String? {
    val fileManager = NSFileManager.defaultManager
    val library = mediaFiles.directory(MediaCollection.Library)
    fileManager.createDirectoryAtPath(library, withIntermediateDirectories = true, attributes = null, error = null)
    val destination = "$library/$fileName"
    if (!fileManager.fileExistsAtPath(destination)) {
        val stored =
            if (move) {
                fileManager.moveItemAtPath(sourcePath, destination, error = null)
            } else {
                fileManager.copyItemAtPath(sourcePath, destination, error = null)
            }
        if (!stored) return null
    }
    return NSURL.fileURLWithPath(destination).absoluteString
}
