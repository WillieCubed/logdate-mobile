@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media

import io.github.aakira.napier.Napier
import kotlinx.io.IOException
import platform.Foundation.NSFileManager

/** Publishes only to a new caller-owned destination, and never accepts a failed platform write. */
internal fun publishIosMediaFile(
    path: String,
    write: () -> Boolean,
) {
    val files = NSFileManager.defaultManager
    if (files.fileExistsAtPath(path)) throw IOException("Media destination already exists")
    try {
        if (!write()) throw IOException("Unable to publish media file")
    } catch (failure: Exception) {
        if (!files.removeItemAtPath(path, error = null) && files.fileExistsAtPath(path)) {
            Napier.w("Could not remove incomplete media publication")
        }
        throw failure
    }
}
