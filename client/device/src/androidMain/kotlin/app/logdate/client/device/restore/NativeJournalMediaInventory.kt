package app.logdate.client.device.restore

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** Source-authenticated private originals; kept separate so the journal receipt stays small. */
@Serializable
data class NativeJournalMediaInventory(
    val version: Int,
    val files: List<Entry>,
) {
    @Serializable
    data class Entry(
        val path: String,
        val size: Long,
        val sha256: String,
    )

    fun entry(path: String): Entry? = files.binarySearchBy(path) { it.path }.takeIf { it >= 0 }?.let(files::get)
}

/** The source writes this before native full backup starts; the receipt hashes its exact bytes. */
fun writeNativeJournalMediaInventory(
    destination: File,
    filesDir: File,
    selectedPaths: Set<String>? = null,
) {
    val files = mutableListOf<NativeJournalMediaInventory.Entry>()
    val base = filesDir.toPath()

    fun capture(
        file: Path,
        path: String,
        attributes: BasicFileAttributes,
    ) {
        if (!attributes.isRegularFile || attributes.size() < 0) return
        val hash = retainedJournalChecksum(file.toFile())
        val after = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (!after.isRegularFile ||
            after.size() != attributes.size() ||
            after.lastModifiedTime() != attributes.lastModifiedTime()
        ) {
            return
        }
        if (path.startsWith("files/media/objects/sha256/") && retainedCanonicalMediaDigest(path) != hash) return
        files += NativeJournalMediaInventory.Entry(path, attributes.size(), hash)
    }

    if (selectedPaths != null) {
        selectedPaths.forEach selected@{ path ->
            if (!validNativeMediaInventoryPath(path, allowStaging = true)) return@selected
            val segments = path.removePrefix("files/").split('/')
            var directory = base
            for (segment in segments.dropLast(1)) {
                directory = directory.resolve(segment)
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return@selected
            }
            val file = directory.resolve(segments.last())
            val attributes =
                runCatching { Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS) }
                    .getOrNull() ?: return@selected
            capture(file, path, attributes)
        }
    } else {
        for (root in MEDIA_ROOTS) {
            val directory = base.resolve(root)
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue
            Files.walkFileTree(
                directory,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        val path = "files/" + base.relativize(file).toString().replace(File.separatorChar, '/')
                        if (validNativeMediaInventoryPath(path)) capture(file, path, attributes)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }
    }
    val inventory = NativeJournalMediaInventory(1, files.sortedBy(NativeJournalMediaInventory.Entry::path))
    val encoded = Json.encodeToString(inventory)
    check(encoded.encodeToByteArray().size <= MAX_INVENTORY_BYTES) {
        "Native media inventory exceeds its bounded format"
    }
    retainedJournalWriteAtomic(destination, encoded)
}

/** A v3 source receipt may authorize media only through this verified inventory. */
fun verifyNativeJournalMediaInventory(
    file: File,
    expectedSha256: String,
): NativeJournalMediaInventory {
    check(expectedSha256.matches(SHA256))
    val encoded = retainedJournalReadBounded(file, MAX_INVENTORY_BYTES)
    val actual =
        MessageDigest.getInstance("SHA-256").digest(encoded.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    check(actual == expectedSha256) { "Native media inventory changed" }
    val inventory = Json.decodeFromString<NativeJournalMediaInventory>(encoded)
    check(inventory.version == 1 && Json.encodeToString(inventory) == encoded)
    check(inventory.files.size <= MAX_INVENTORY_ENTRIES)
    var previous = ""
    inventory.files.forEach { entry ->
        check(validNativeMediaInventoryPath(entry.path, allowStaging = true) && entry.path > previous)
        check(entry.size >= 0 && entry.sha256.matches(SHA256))
        if (entry.path.startsWith("files/media/objects/sha256/")) check(retainedCanonicalMediaDigest(entry.path) == entry.sha256)
        previous = entry.path
    }
    return inventory
}

private fun validNativeMediaInventoryPath(
    path: String,
    allowStaging: Boolean = false,
): Boolean {
    val relative = path.removePrefix("files/")
    if (relative == path || MEDIA_ROOTS.none { relative.startsWith("$it/") }) return false
    val parts = relative.split('/')
    if (relative.length > 1024 ||
        parts.any { part ->
            part.isEmpty() || part == "." || part == ".." || '\\' in part || part.any { it.code < 32 }
        }
    ) {
        return false
    }
    if (!allowStaging && (relative.startsWith("audio_notes/staging/") || relative.startsWith("media/staging/"))) return false
    return !relative.startsWith("media/objects/sha256/") || retainedCanonicalMediaDigest(path) != null
}

private fun retainedCanonicalMediaDigest(path: String): String? {
    val matched = CANONICAL_MEDIA.matchEntire(path) ?: return null
    return matched.groupValues[2].lowercase().takeIf { it.startsWith(matched.groupValues[1], ignoreCase = true) }
}

const val NATIVE_MEDIA_INVENTORY_PATH = "files/db_recovery/native_media_inventory.json"
private val MEDIA_ROOTS = listOf("media", "user_media", "audio_notes")
private val CANONICAL_MEDIA =
    Regex("files/media/objects/sha256/([a-f0-9]{2})/([a-f0-9]{64})(?:\\.retained-[1-9][0-9]*)?\\.[^/.]+", RegexOption.IGNORE_CASE)
private val SHA256 = Regex("[a-f0-9]{64}")
private const val MAX_INVENTORY_BYTES = 128 * 1024 * 1024
private const val MAX_INVENTORY_ENTRIES = 500_000
