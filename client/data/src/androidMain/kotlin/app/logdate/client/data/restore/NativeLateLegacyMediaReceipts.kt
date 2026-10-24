package app.logdate.client.data.restore

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Binds a post-import canonical object to one authenticated legacy revision across a CAS restart. */
internal class NativeLateLegacyMediaReceipts(
    private val root: File,
    private val sourceReceiptSha256: String,
) {
    @Serializable
    private data class Record(
        val source: String,
        val path: String,
        val sha256: String,
        val canonical: String,
    )

    init {
        check(sourceReceiptSha256.matches(Regex("[a-f0-9]{64}")))
    }

    fun read(
        path: String,
        sha256: String,
    ): String? {
        val file = file(path)
        if (!file.exists()) return null
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && file.length() in 1..8192)
        val record = Json.decodeFromString<Record>(file.readText())
        check(record.source == sourceReceiptSha256 && record.path == path)
        return record.canonical.takeIf { record.sha256 == sha256 && retainedCanonicalMediaDigest(it) == sha256 }
    }

    fun remember(
        candidate: NativeLegacyLateMediaCandidate,
        canonical: String,
    ) {
        check(retainedCanonicalMediaDigest(canonical) == candidate.sha256)
        val destination = file(candidate.logicalPath)
        val parent = checkNotNull(destination.parentFile)
        check(parent.mkdirs() || parent.isDirectory)
        val temporary = File.createTempFile(".legacy-", ".part", parent)
        try {
            FileOutputStream(temporary).use {
                it.write(
                    Json
                        .encodeToString(
                            Record(sourceReceiptSha256, candidate.logicalPath, candidate.sha256, canonical),
                        ).encodeToByteArray(),
                )
                it.fd.sync()
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            FileChannel.open(parent.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } finally {
            temporary.delete()
        }
    }

    private fun file(path: String): File = File(root, "$sourceReceiptSha256/${nativeSnapshotChecksumText(path)}.json")
}

private fun nativeSnapshotChecksumText(value: String): String =
    java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { "%02x".format(it) }
