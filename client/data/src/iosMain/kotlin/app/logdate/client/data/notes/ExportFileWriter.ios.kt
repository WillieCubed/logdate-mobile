@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class
)

package app.logdate.client.data.notes

import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile

/**
 * Writes export content to a file path on iOS using UTF-8 encoding.
 *
 * @param destination Absolute file path to write to.
 * @param content Export payload to persist.
 * @param overwrite Whether to allow overwriting an existing file.
 */
internal actual fun writeExportFile(
    destination: String,
    content: String,
    overwrite: Boolean
) {
    val fileManager = NSFileManager.defaultManager
    if (!overwrite && fileManager.fileExistsAtPath(destination)) {
        throw IllegalStateException("File already exists and overwrite is set to false.")
    }

    val data = NSString.create(string = content)
        .dataUsingEncoding(NSUTF8StringEncoding)
        ?: throw IllegalStateException("Failed to encode export content.")

    if (!data.writeToFile(destination, true)) {
        throw IllegalStateException("Failed to write export file.")
    }
}
