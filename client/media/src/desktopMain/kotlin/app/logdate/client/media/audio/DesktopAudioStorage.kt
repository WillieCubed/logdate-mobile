package app.logdate.client.media.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.uuid.Uuid

/**
 * Desktop implementation of [AudioStorage] that stores recordings under a per-user directory.
 */
class DesktopAudioStorage : AudioStorage {
    override suspend fun createRecordingTarget(extension: String): AudioRecordingTarget =
        withContext(Dispatchers.IO) {
            val safeExtension = extension.trimStart('.').ifBlank { "wav" }
            val directory = resolveAudioDirectory().also { Files.createDirectories(it) }

            val baseName = "recording_${Uuid.random()}"
            val targetPath = directory.resolve("$baseName.$safeExtension")

            val targetFile = targetPath.toFile()
            if (targetFile.exists()) {
                targetFile.delete()
            }

            AudioRecordingTarget(path = targetFile.absolutePath)
        }

    private fun resolveAudioDirectory(): Path {
        val home = System.getProperty("user.home") ?: "."
        return Paths.get(home, ".logdate", "audio_notes")
    }
}
