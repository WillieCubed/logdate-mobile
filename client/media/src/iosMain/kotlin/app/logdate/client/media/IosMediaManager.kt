@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package app.logdate.client.media

import io.github.aakira.napier.Napier
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.memcpy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * iOS implementation of MediaManager backed by app-scoped storage.
 */
class IosMediaManager(
    private val mediaRootPath: String = defaultMediaPath(),
) : MediaManager {
    private val fileManager = NSFileManager.defaultManager
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "mov", "m4v")

    override suspend fun getMedia(uri: String): MediaObject =
        withContext(Dispatchers.Default) {
            val path = resolvePath(uri)
            val media = path?.let { toMediaObject(it) }
            return@withContext media ?: error("Unsupported or missing media at $uri")
        }

    override suspend fun exists(mediaId: String): Boolean =
        withContext(Dispatchers.Default) {
            val path = resolvePath(mediaId)
            path != null && fileManager.fileExistsAtPath(path)
        }

    override suspend fun getRecentMedia(): Flow<List<MediaObject>> =
        withContext(Dispatchers.Default) {
            val media =
                listMediaObjects()
                    .sortedByDescending { it.timestamp }
                    .take(MAX_RECENT_MEDIA)
            flowOf(media)
        }

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> =
        withContext(Dispatchers.Default) {
            val media =
                listMediaObjects()
                    .filter { it.timestamp >= start && it.timestamp < end }
            flowOf(media)
        }

    override suspend fun addToDefaultCollection(uri: String) =
        withContext(Dispatchers.Default) {
            val sourcePath = resolvePath(uri) ?: return@withContext
            ensureMediaDir()
            if (sourcePath.startsWith(mediaRootPath)) {
                return@withContext
            }
            val destination = buildMediaPath(sourcePath.substringAfterLast('/'))
            if (!fileManager.fileExistsAtPath(destination)) {
                fileManager.copyItemAtPath(sourcePath, destination, error = null)
            }
        }

    override suspend fun readMedia(uri: String): MediaPayload =
        withContext(Dispatchers.Default) {
            val path = resolvePath(uri) ?: error("Invalid media URI: $uri")
            val data = fileManager.contentsAtPath(path) ?: error("Unable to read media at $uri")
            val fileName = path.substringAfterLast('/')
            val mimeType = guessMimeType(fileName)
            return@withContext MediaPayload(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = data.length.toLong(),
                data = data.toByteArray(),
            )
        }

    override suspend fun saveMedia(payload: MediaPayload): String =
        withContext(Dispatchers.Default) {
            ensureMediaDir()
            val sanitizedName =
                payload.fileName
                    .replace("..", "_")
                    .replace("/", "_")
                    .replace("\\", "_")
            val fileName = "${Uuid.random()}-$sanitizedName"
            val path = buildMediaPath(fileName)
            val data = payload.data.toNSData()
            if (!data.writeToFile(path, true)) {
                Napier.e("Failed to write media payload to $path")
            }
            return@withContext "file://$path"
        }

    private fun listMediaObjects(): List<MediaObject> {
        ensureMediaDir()
        val files = fileManager.contentsOfDirectoryAtPath(mediaRootPath, error = null) ?: return emptyList()
        return files
            .filterIsInstance<String>()
            .mapNotNull { fileName -> toMediaObject("$mediaRootPath/$fileName") }
    }

    private fun toMediaObject(path: String): MediaObject? {
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null
        val name = path.substringAfterLast('/')
        val attributes = fileManager.attributesOfItemAtPath(path, error = null)
        val size = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
        val modifiedAt = attributes?.get(NSFileModificationDate) as? NSDate
        val timestamp =
            modifiedAt?.let { date ->
                Instant.fromEpochMilliseconds((date.timeIntervalSince1970 * 1000).toLong())
            } ?: Instant.fromEpochMilliseconds(0)

        return when {
            imageExtensions.contains(extension) ->
                MediaObject.Image(
                    uri = "file://$path",
                    size = size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    name = name,
                    timestamp = timestamp,
                )
            videoExtensions.contains(extension) ->
                MediaObject.Video(
                    name = name,
                    uri = "file://$path",
                    size = size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    timestamp = timestamp,
                    duration = resolveDuration(path),
                )
            else -> null
        }
    }

    private fun resolveDuration(path: String): Duration {
        val url = NSURL.fileURLWithPath(path)
        val asset = AVURLAsset.URLAssetWithURL(url, null)
        val seconds = CMTimeGetSeconds(asset.duration)
        if (seconds.isNaN() || seconds <= 0) {
            return Duration.ZERO
        }
        return (seconds * 1000).toLong().milliseconds
    }

    private fun resolvePath(uri: String): String? =
        if (uri.startsWith("file://")) {
            NSURL.URLWithString(uri)?.path
        } else {
            uri
        }

    private fun ensureMediaDir() {
        fileManager.createDirectoryAtPath(
            mediaRootPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    private fun buildMediaPath(fileName: String): String = "$mediaRootPath/$fileName"

    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
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

    private fun ByteArray.toNSData(): NSData =
        usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) {
            return ByteArray(0)
        }
        val buffer = ByteArray(length)
        buffer.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length.toULong())
        }
        return buffer
    }

    private companion object {
        private const val MAX_RECENT_MEDIA = 50
    }
}

private fun defaultMediaPath(): String {
    val fileManager = NSFileManager.defaultManager
    val url: NSURL? =
        fileManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    val basePath = requireNotNull(url?.path)
    return "$basePath/media"
}
