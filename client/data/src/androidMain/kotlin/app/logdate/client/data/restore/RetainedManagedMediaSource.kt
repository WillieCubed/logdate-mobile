package app.logdate.client.data.restore

import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaReference
import app.logdate.client.media.storage.UnavailableMediaReferences
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/** Resolves only media bytes included in one verified retained source generation. */
internal class RetainedManagedMediaSource(
    private val files: Map<String, File>,
    private val additionalSources: List<Map<String, File>> = emptyList(),
) {
    data class Asset(
        val file: File,
        val fileName: String,
        val mimeType: String,
    )

    fun resolve(reference: String): Asset? {
        val relative =
            when (val parsed = MediaReference.parse(UnavailableMediaReferences.nativeSource(reference) ?: reference)) {
                is MediaReference.Owned -> collectionPath(parsed.ref.collection, parsed.ref.path)
                is MediaReference.LocalFile -> legacyPath(parsed.path) ?: parsed.alternatePath?.let(::legacyPath)
                is MediaReference.External -> null
            } ?: return null
        val key = "files/$relative"
        val digest = retainedCanonicalMediaDigest(reference)
        val candidates =
            sequence {
                files[key]?.let { yield(it) }
                if (digest != null) additionalSources.forEach { it[key]?.let { file -> yield(file) } }
            }
        for (file in candidates) {
            val asset = resolveFile(file, relative, digest)
            if (asset != null) return asset
        }
        return null
    }

    private fun resolveFile(
        file: File,
        relative: String,
        digest: String?,
    ): Asset? {
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file.toPath())) return null
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val parent = runCatching { file.parentFile?.canonicalFile }.getOrNull() ?: return null
        if (canonical.parentFile != parent || canonical.name != file.name) return null
        if (digest != null && !matchesDigest(canonical, digest)) return null
        val originalName = relative.substringAfterLast('/')
        val mime = mimeType(originalName) ?: return null
        return Asset(canonical, originalName, mime)
    }

    private fun matchesDigest(
        file: File,
        expected: String,
    ): Boolean =
        runCatching {
            if (file.length() <= 0) return@runCatching false
            val hashing = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) return@runCatching false
                    hashing.update(buffer, 0, count)
                }
            }
            hashing.digest().joinToString("") { "%02x".format(it) } == expected
        }.getOrDefault(false)

    private fun legacyPath(path: String): String? {
        val normalized = runCatching { URI(path).path ?: path }.getOrDefault(path).replace('\\', '/')
        val marker = "/files/"
        val index = normalized.lastIndexOf(marker)
        if (index < 0) return null
        val packagePath = normalized.substring(0, index)
        if (!packagePath.endsWith("/studio.hypertext.logdate") && !packagePath.endsWith("/co.reasonabletech.logdate")) return null
        val relative = normalized.substring(index + marker.length)
        if (!relative.startsWith("media/") && !relative.startsWith("audio_notes/")) return null
        if (relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        return relative
    }

    private fun collectionPath(
        collection: MediaCollection,
        path: String,
    ): String =
        when (collection) {
            MediaCollection.Library -> "media/$path"
            MediaCollection.Recordings -> "audio_notes/$path"
        }

    private fun mimeType(name: String): String? =
        when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            else -> null
        }
}

/** Cross-generation recovery needs a content digest; legacy path equality conveys no authority. */
internal fun retainedCanonicalMediaDigest(reference: String): String? {
    val original = UnavailableMediaReferences.nativeSource(reference) ?: reference
    val owned = (MediaReference.parse(original) as? MediaReference.Owned)?.ref ?: return null
    if (owned.collection != MediaCollection.Library) return null
    val matched = RETAINED_CANONICAL_OBJECT.matchEntire(owned.path) ?: return null
    val digest = matched.groupValues[2].lowercase()
    return digest.takeIf { it.startsWith(matched.groupValues[1], ignoreCase = true) }
}

internal suspend fun verifiedCanonicalMediaReference(
    reference: String,
    verify: suspend (String) -> Boolean,
): String? {
    if (retainedCanonicalMediaDigest(reference) == null) return null
    val original = UnavailableMediaReferences.nativeSource(reference) ?: reference
    return original.takeIf { verify(it) }
}

private val RETAINED_CANONICAL_OBJECT = Regex("objects/sha256/([a-f0-9]{2})/([a-f0-9]{64})\\.[^/.]+", RegexOption.IGNORE_CASE)
