package app.logdate.client.device.restore

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

internal fun retainedJournalReadBounded(
    file: File,
    maximum: Int,
): String {
    check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
    val bytes = ByteArray(maximum + 1)
    var count = 0
    file.inputStream().use { input ->
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read < 0) break
            count += read
        }
    }
    check(count in 1..maximum)
    return bytes.copyOf(count).decodeToString(throwOnInvalidSequence = true)
}

internal fun retainedJournalWriteAtomic(
    file: File,
    value: String,
) = retainedJournalWriteAtomic(file, value.encodeToByteArray())

internal fun retainedJournalWriteAtomic(
    file: File,
    value: ByteArray,
) {
    retainedJournalDurableDirectory(checkNotNull(file.parentFile))
    val temporary = File.createTempFile(".retained-journal-", ".part", file.parentFile)
    try {
        FileOutputStream(temporary).use { output ->
            output.write(value)
            output.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        retainedJournalSyncDirectory(checkNotNull(file.parentFile))
    } finally {
        temporary.delete()
    }
}

internal fun retainedJournalChecksum(file: File): String =
    file.inputStream().use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

internal fun retainedJournalDurableDirectory(directory: File) {
    if (directory.isDirectory) return
    val parent = checkNotNull(directory.parentFile)
    retainedJournalDurableDirectory(parent)
    check(directory.mkdir() || directory.isDirectory)
    retainedJournalSyncDirectory(parent)
}

internal fun retainedJournalSyncDirectory(directory: File) {
    FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
}
