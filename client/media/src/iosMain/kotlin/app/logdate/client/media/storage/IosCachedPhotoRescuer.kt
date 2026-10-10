@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media.storage

import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * Copies photos and videos an earlier build stored as paths in the system cache into the media
 * library.
 *
 * Builds before the media library import kept photos picked from Photos in
 * `Library/Caches/photo-library-renderable`, which iOS may empty at any time and does not restore
 * from a backup. While such a file is still there it is copied into the library; once iOS has
 * emptied the cache the photo can no longer be found and the entry keeps its old reference.
 */
class IosCachedPhotoRescuer(
    private val mediaFiles: MediaFileResolver,
) : MediaRescuer {
    override suspend fun rescue(reference: String): String? {
        val path = mediaFiles.filePath(reference) ?: return null
        if (CACHE_FOLDER !in path || !NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        val imported = importFileIntoLibrary(mediaFiles, path, path.substringAfterLast('/')) ?: return null
        return mediaFiles.storedReference(imported)
    }

    private companion object {
        const val CACHE_FOLDER = "/Library/Caches/photo-library-renderable/"
    }
}

/**
 * Copies the file at [sourcePath] into the media library as [fileName] unless that name is already
 * there, and returns the stored file's URL, or `null` when it could not be copied.
 */
internal fun importFileIntoLibrary(
    mediaFiles: MediaFileResolver,
    sourcePath: String,
    fileName: String,
): String? {
    val fileManager = NSFileManager.defaultManager
    val library = mediaFiles.directory(MediaCollection.Library)
    fileManager.createDirectoryAtPath(library, withIntermediateDirectories = true, attributes = null, error = null)
    val destination = "$library/$fileName"
    if (!fileManager.fileExistsAtPath(destination) && !fileManager.copyItemAtPath(sourcePath, destination, error = null)) return null
    return NSURL.fileURLWithPath(destination).absoluteString
}
