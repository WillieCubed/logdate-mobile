package app.logdate.client.data.restore

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/** Strict private atomic file adapter for the bounded per-entry checkpoint ledger. */
internal class RetainedJournalMergeCheckpointStore(
    directory: File,
) {
    private val ledger = RetainedJournalMergeCheckpointLedger(AtomicCheckpointRecords(directory))
    private val lock = locks.computeIfAbsent(directory.absolutePath) { Any() }

    fun read(key: String): RetainedJournalMergeCheckpoint.Entry? = synchronized(lock) { ledger.read(key) }

    fun write(
        key: String,
        entry: RetainedJournalMergeCheckpoint.Entry,
    ) = synchronized(lock) { ledger.write(key, entry) }

    fun markApplied(
        key: String,
        targetSha256: String,
    ) = synchronized(lock) { ledger.markApplied(key, targetSha256) }

    fun allApplied(generationId: String): Boolean = synchronized(lock) { ledger.allApplied(generationId) }

    private companion object {
        val locks = ConcurrentHashMap<String, Any>()
    }
}

internal fun retainedJournalMergeCheckpointDirectory(
    noBackupFilesDir: File,
    generation: String,
): File {
    require(generation.matches(Regex("[A-Za-z0-9-]+")))
    return File(noBackupFilesDir.canonicalFile, "retained-journal-merges/$generation")
}

internal fun requireCanonicalCheckpointPath(path: File): File {
    val canonical = path.canonicalFile
    require(canonical == path.absoluteFile) { "Recovery checkpoint directory changed through a symlink" }
    return canonical
}

private class AtomicCheckpointRecords(
    private val directory: File,
) : CheckpointRecords {
    override fun read(name: String): String? {
        val file = recordFile(name)
        if (!file.exists()) return null
        requireRegularFile(file)
        require(file.length() in 1..RetainedJournalMergeCheckpointLedger.MAX_ENTRY_BYTES) {
            "Recovery checkpoint entry exceeds its size limit"
        }
        return file.readText()
    }

    override fun writeAtomically(
        name: String,
        value: String,
    ) {
        ensurePrivateDirectory(directory)
        val target = recordFile(name)
        val temporary = File(directory, ".$name.tmp")
        require(!Files.isSymbolicLink(target.toPath()) && !Files.isSymbolicLink(temporary.toPath()))
        FileOutputStream(temporary).use { output ->
            output.write(value.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            check(temporary.renameTo(target)) { "Unable to publish recovery checkpoint entry" }
        }
        syncDirectory(directory)
    }

    override fun forEachRecord(visit: (String) -> Boolean) {
        if (!directory.exists()) return
        requirePrivateDirectory(directory)
        Files.newDirectoryStream(directory.toPath()).use { files: DirectoryStream<java.nio.file.Path> ->
            for (path in files) {
                val file = path.toFile()
                if (!file.name.endsWith(RetainedJournalMergeCheckpointLedger.CHECKPOINT_SUFFIX)) continue
                requireRegularFile(file)
                require(file.length() in 1..RetainedJournalMergeCheckpointLedger.MAX_ENTRY_BYTES) {
                    "Recovery checkpoint entry exceeds its size limit"
                }
                if (!visit(file.readText())) return
            }
        }
    }

    private fun recordFile(name: String): File {
        require(name.matches(RECORD_NAME))
        val canonicalDirectory = requireCanonicalCheckpointPath(directory)
        val file = File(canonicalDirectory, name)
        require(file.canonicalFile.parentFile == canonicalDirectory && file.name == name)
        return file
    }

    private fun ensurePrivateDirectory(path: File) {
        val canonical = requireCanonicalCheckpointPath(path)
        require(canonical.isDirectory || canonical.mkdirs()) { "Unable to create recovery checkpoint directory" }
        requirePrivateDirectory(canonical)
    }

    private fun requirePrivateDirectory(path: File) {
        check(Files.isDirectory(path.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path.toPath())) {
            "Recovery checkpoint directory cannot be a symlink"
        }
    }

    private fun requireRegularFile(file: File) {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file.toPath())) {
            "Recovery checkpoint is not a regular file"
        }
    }

    private fun syncDirectory(file: File) {
        val descriptor = Os.open(file.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private companion object {
        val RECORD_NAME = Regex("[0-9a-f]{64}\\.checkpoint\\.json")
    }
}
