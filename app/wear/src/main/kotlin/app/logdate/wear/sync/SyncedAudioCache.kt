package app.logdate.wear.sync

import io.github.aakira.napier.Napier
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/**
 * Audio pulled from the phone so the watch can play a phone note, stored apart from the watch's own
 * recordings.
 *
 * A note's audio lives at `<directory>/<noteId>.<extension>`. A download is written to a `.part` file
 * beside it and renamed into place only once complete, so a transfer cut short by the app being
 * killed never looks like a finished file. Crash recovery only scans the recordings directory, so
 * nothing here can be mistaken for an interrupted recording and saved as a new note.
 */
class SyncedAudioCache(
    private val directory: File,
) {
    /** The complete audio already pulled for [noteId], or null when there is none. */
    fun cachedFile(noteId: Uuid): File? =
        directory
            .listFiles()
            .orEmpty()
            .firstOrNull { it.isFile && it.nameWithoutExtension == noteId.toString() }

    /**
     * Pulls the audio for [noteId]: [download] writes it to the path it is given, and the file becomes
     * the note's audio only when [download] reports success and wrote something.
     *
     * @return the complete file, or null when nothing was stored.
     */
    suspend fun pull(
        noteId: Uuid,
        extension: String,
        download: suspend (partPath: String) -> Boolean,
    ): File? {
        directory.mkdirs()
        clearAbandonedParts()
        val part = File(directory, "$noteId.$extension.$PART_EXTENSION")
        val complete = download(part.absolutePath) && part.length() > 0L
        if (!complete) {
            part.delete()
            return null
        }
        val target = File(directory, "$noteId.$extension")
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return target
    }

    /** Removes the audio pulled for [noteId]. @return true when a file was deleted. */
    fun delete(noteId: Uuid): Boolean = cachedFile(noteId)?.delete() ?: false

    private fun clearAbandonedParts() {
        val cutoff = System.currentTimeMillis() - ABANDONED_PART_AGE_MS
        directory
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == PART_EXTENSION && it.lastModified() < cutoff }
            .forEach { abandoned ->
                if (abandoned.delete()) Napier.d("Cleared abandoned audio download ${abandoned.name}")
            }
    }

    companion object {
        const val DIRECTORY_NAME = "synced_audio"
        private const val PART_EXTENSION = "part"

        /** A download writes continuously, so a part file untouched for an hour was abandoned. */
        private const val ABANDONED_PART_AGE_MS = 60L * 60 * 1000
    }
}
