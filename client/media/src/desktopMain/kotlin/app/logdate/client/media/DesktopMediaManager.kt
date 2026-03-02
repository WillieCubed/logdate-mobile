package app.logdate.client.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.streams.asSequence
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DesktopMediaManager : MediaManager {
    override suspend fun getMedia(uri: String): MediaObject {
        val path = resolvePath(uri)
        return toMediaObject(path) ?: error("Unsupported or missing media at $uri")
    }

    override suspend fun exists(mediaId: String): Boolean = Files.exists(resolvePath(mediaId))

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> {
        val media =
            listMediaObjects()
                .sortedByDescending { it.timestamp }
                .take(MAX_RECENT_MEDIA)
        return flowOf(media)
    }

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> {
        val media =
            listMediaObjects()
                .filter { it.timestamp >= start && it.timestamp < end }
        return flowOf(media)
    }

    override suspend fun addToDefaultCollection(uri: String) {
        val source = resolvePath(uri)
        ensureMediaDir()
        if (source.startsWith(mediaRoot)) {
            return
        }
        val sanitizedName =
            source.name
                .replace("..", "_")
                .replace("/", "_")
                .replace("\\", "_")
        val targetName = "${Uuid.random()}-$sanitizedName"
        val target = mediaRoot.resolve(targetName)
        if (!Files.exists(target)) {
            Files.copy(source, target)
        }
    }

    override suspend fun readMedia(uri: String): MediaPayload {
        val path = resolvePath(uri)
        val data = Files.readAllBytes(path)
        val fileName = path.name
        val mimeType = Files.probeContentType(path) ?: guessMimeType(fileName)
        return MediaPayload(
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = data.size.toLong(),
            data = data,
        )
    }

    override suspend fun saveMedia(payload: MediaPayload): String {
        val directory = ensureMediaDir()
        val sanitizedName =
            payload.fileName
                .replace("..", "_")
                .replace("/", "_")
                .replace("\\", "_")
        val fileName = "${Uuid.random()}-$sanitizedName"
        val filePath = directory.resolve(fileName)
        Files.write(filePath, payload.data)
        return "file://${filePath.pathString}"
    }

    private fun resolvePath(uri: String): Path =
        if (uri.startsWith("file://")) {
            Path.of(URI(uri))
        } else {
            Path.of(uri)
        }

    private fun ensureMediaDir(): Path {
        val directory = mediaRoot
        directory.createDirectories()
        return directory
    }

    private fun listMediaObjects(): List<MediaObject> {
        if (!Files.exists(mediaRoot)) return emptyList()
        return Files.list(mediaRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .asSequence()
                .mapNotNull(::toMediaObject)
                .toList()
        }
    }

    private fun toMediaObject(path: Path): MediaObject? {
        val extension = path.extension.lowercase()
        val name = path.name
        val size = Files.size(path).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val lastModified = Files.getLastModifiedTime(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
        val timestamp = fileTimeToInstant(lastModified)

        return when (extension) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif" ->
                MediaObject.Image(
                    uri = "file://${path.pathString}",
                    size = size,
                    name = name,
                    timestamp = timestamp,
                )
            "mp4", "mov", "m4v" ->
                MediaObject.Video(
                    name = name,
                    uri = "file://${path.pathString}",
                    size = size,
                    timestamp = timestamp,
                    duration = Duration.ZERO,
                )
            else -> null
        }
    }

    private fun fileTimeToInstant(fileTime: FileTime): Instant = Instant.fromEpochMilliseconds(fileTime.toMillis())

    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        if (extension.isBlank()) {
            return "application/octet-stream"
        }
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "mp4" -> "video/mp4"
            "mov", "m4v" -> "video/quicktime"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        private const val MAX_RECENT_MEDIA = 50
        private val mediaRoot: Path = Path.of(System.getProperty("user.home"), ".logdate", "media")
    }
}
