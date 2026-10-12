package app.logdate.wear.sync

import io.github.aakira.napier.Napier
import java.io.File
import java.io.IOException
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
    private val lock = Any()
    private val leases = mutableMapOf<Uuid, MutableSet<DownloadLease>>()

    internal class DownloadLease {
        var valid = true
            internal set
    }

    internal suspend fun withDownloadLease(
        noteId: Uuid,
        block: suspend (DownloadLease) -> File?,
    ): File? {
        val lease =
            synchronized(lock) {
                DownloadLease().also { leases.getOrPut(noteId) { mutableSetOf() }.add(it) }
            }
        return try {
            block(lease)
        } finally {
            synchronized(lock) {
                leases[noteId]?.let {
                    it.remove(lease)
                    if (it.isEmpty()) leases.remove(noteId)
                }
            }
        }
    }

    internal fun isValid(lease: DownloadLease): Boolean = synchronized(lock) { lease.valid }

    internal fun checkValid(lease: DownloadLease) {
        if (!isValid(lease)) throw IOException("The downloaded note was evicted")
    }

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
    ): File? = withDownloadLease(noteId) { lease -> pull(noteId, extension, lease, download) }

    internal suspend fun pull(
        noteId: Uuid,
        extension: String,
        lease: DownloadLease,
        download: suspend (partPath: String) -> Boolean,
    ): File? {
        if (!isValid(lease)) return null
        directory.mkdirs()
        clearAbandonedParts()
        val part = Files.createTempFile(directory.toPath(), "$noteId.$extension.", ".$PART_EXTENSION").toFile()
        return try {
            if (!download(part.absolutePath) || part.length() <= 0L) return null
            synchronized(lock) {
                if (!lease.valid) return@synchronized null
                val target = File(directory, "$noteId.$extension")
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                target
            }
        } finally {
            part.delete()
        }
    }

    /** Invalidates queued and active pulls, then removes the complete audio if present. */
    fun delete(noteId: Uuid): Boolean =
        synchronized(lock) {
            leases[noteId]?.forEach { it.valid = false }
            directory
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.nameWithoutExtension == noteId.toString() }
                .fold(false) { deleted, file -> file.delete() || deleted }
        }

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
