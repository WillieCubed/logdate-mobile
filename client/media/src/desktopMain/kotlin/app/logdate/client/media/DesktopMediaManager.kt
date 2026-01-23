package app.logdate.client.media

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.uuid.Uuid

class DesktopMediaManager : MediaManager {
    override suspend fun getMedia(uri: String): MediaObject {
        TODO("Not yet implemented")
    }

    override suspend fun exists(mediaId: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> {
        TODO("Not yet implemented")
    }

    override suspend fun queryMediaByDate(start: Instant, end: Instant): Flow<List<MediaObject>> {
        TODO("Not yet implemented")
    }

    override suspend fun addToDefaultCollection(uri: String) {
        TODO("Not yet implemented")
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
            data = data
        )
    }

    override suspend fun saveMedia(payload: MediaPayload): String {
        val directory = Path.of(System.getProperty("user.home"), ".logdate", "media")
        directory.createDirectories()
        val sanitizedName = payload.fileName.replace("..", "_").replace("/", "_").replace("\\", "_")
        val fileName = "${Uuid.random()}-$sanitizedName"
        val filePath = directory.resolve(fileName)
        Files.write(filePath, payload.data)
        return "file://${filePath.pathString}"
    }

    private fun resolvePath(uri: String): Path {
        return if (uri.startsWith("file://")) {
            Path.of(URI(uri))
        } else {
            Path.of(uri)
        }
    }

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
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }
}
