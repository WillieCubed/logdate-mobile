package app.logdate.client.media.storage

import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.uuid.Uuid

/** App-private write-ahead list of media that may become unclaimed after a repository mutation. */
class AndroidMediaReclamationJournal(
    private val noBackupFilesDir: File,
) : MediaReclamationJournal {
    private val directory = File(noBackupFilesDir, "media-reclamation")

    override suspend fun recordCandidate(reference: String) =
        locked {
            val bytes = reference.encodeToByteArray()
            require(bytes.isNotEmpty() && bytes.size <= MAX_REFERENCE_BYTES)
            val target = candidateFile(reference)
            if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                check(readCandidate(target) == reference) { "Media reclamation candidate changed" }
                syncDirectory(directory)
                return@locked
            }
            val temporary = File(directory, "${Uuid.random()}.part")
            try {
                FileChannel
                    .open(
                        temporary.toPath(),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS,
                    ).use { output ->
                        val buffer = ByteBuffer.wrap(bytes)
                        while (buffer.hasRemaining()) output.write(buffer)
                        output.force(true)
                    }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                syncDirectory(directory)
            } finally {
                temporary.delete()
            }
        }

    suspend fun pendingCandidates(): List<String> =
        locked {
            checkNotNull(directory.listFiles()) { "Could not read media reclamation candidates" }
                .filter { it.name.endsWith(CANDIDATE_SUFFIX) }
                .map(::readCandidate)
                .sorted()
        }

    private suspend fun <T> locked(block: () -> T): T =
        withContext(Dispatchers.IO) {
            processMutex.withLock {
                if (!directory.isDirectory) {
                    check(directory.mkdirs() || directory.isDirectory) { "Could not create media reclamation directory" }
                    syncDirectory(noBackupFilesDir)
                }
                check(!Files.isSymbolicLink(directory.toPath())) { "Media reclamation directory is a symlink" }
                FileChannel
                    .open(
                        File(directory, ".lock").toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS,
                    ).use { channel ->
                        channel.lock().use { block() }
                    }
            }
        }

    private fun candidateFile(reference: String): File = File(directory, "${digest(reference.encodeToByteArray())}$CANDIDATE_SUFFIX")

    private fun readCandidate(file: File): String {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Invalid media reclamation candidate" }
        check(file.length() in 1..MAX_REFERENCE_BYTES.toLong()) { "Invalid media reclamation candidate length" }
        val bytes = file.readBytes()
        check("${digest(bytes)}$CANDIDATE_SUFFIX" == file.name) { "Media reclamation candidate digest changed" }
        return bytes.decodeToString(throwOnInvalidSequence = true)
    }

    private fun syncDirectory(path: File) {
        if (System.getProperty("java.vm.name") != "Dalvik") return
        val descriptor = Os.open(path.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        val processMutex = Mutex()
        const val CANDIDATE_SUFFIX = ".candidate"
        const val MAX_REFERENCE_BYTES = 4096
    }
}
