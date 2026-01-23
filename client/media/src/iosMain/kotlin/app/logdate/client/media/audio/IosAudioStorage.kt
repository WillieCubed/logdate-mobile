@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media.audio

import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlin.uuid.Uuid

/**
 * iOS implementation of [AudioStorage] that stores recordings in app-private files.
 */
class IosAudioStorage : AudioStorage {
    override suspend fun createRecordingTarget(
        noteId: Uuid?,
        extension: String,
    ): AudioRecordingTarget = withContext(Dispatchers.Default) {
        val safeExtension = extension.trimStart('.').ifBlank { "m4a" }
        val fileManager = NSFileManager.defaultManager

        val baseDirectory = fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: fileManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )

        requireNotNull(baseDirectory) { "Unable to resolve app storage directory for audio recordings." }

        val audioDir = requireNotNull(baseDirectory.URLByAppendingPathComponent("audio_notes")) {
            "Unable to resolve audio notes directory."
        }
        fileManager.createDirectoryAtURL(
            audioDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        val timestamp = Clock.System.now().toEpochMilliseconds()
        val baseName = noteId?.toString() ?: "recording_$timestamp"
        val targetUrl = requireNotNull(audioDir.URLByAppendingPathComponent("$baseName.$safeExtension")) {
            "Unable to resolve audio recording URL."
        }

        val path = targetUrl.path
        if (path == null) {
            Napier.e("Failed to resolve audio recording path for $targetUrl")
            throw IllegalStateException("Unable to resolve audio recording path.")
        }

        AudioRecordingTarget(noteId = noteId, path = path)
    }
}
