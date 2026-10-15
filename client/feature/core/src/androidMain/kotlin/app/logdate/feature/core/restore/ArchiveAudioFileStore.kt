package app.logdate.feature.core.restore

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/** Stages in cache and commits a fresh recording; archive names never identify accepted files. */
internal class ArchiveAudioFileStore(
    private val directory: File,
    private val stagingDirectory: File,
) {
    fun import(
        source: File,
        fileName: String,
    ): File {
        directory.mkdirs()
        stagingDirectory.mkdirs()
        val extension = fileName.substringAfterLast('.', "bin").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"
        val destination = File(directory, "restored-${Uuid.random()}.$extension")
        val temporary = File.createTempFile("restored-audio-", ".part", stagingDirectory)
        try {
            source.inputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return destination
        } finally {
            temporary.delete()
        }
    }
}
