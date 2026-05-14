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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSCachesDirectory
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
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaTypeAudio
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetMediaTypeVideo
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAssetResourceTypeFullSizePhoto
import platform.Photos.PHAssetResourceTypeFullSizeVideo
import platform.Photos.PHAssetResourceTypePairedVideo
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAssetResourceTypeVideo
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHFetchOptions
import platform.Photos.PHPhotoLibrary
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * iOS implementation of MediaManager backed by app-scoped storage and the user's Photos library.
 *
 * Photos-library assets are represented with stable `ph://<localIdentifier>` URIs so the editor
 * can pass selected images through save/sync flows before they are copied into app storage.
 */
class IosMediaManager(
    private val mediaRootPath: String = defaultMediaPath(),
) : MediaManager {
    private val fileManager = NSFileManager.defaultManager
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "mov", "m4v")

    override suspend fun getMedia(uri: String): MediaObject =
        withContext(Dispatchers.Default) {
            if (uri.isPhotoLibraryUri()) {
                val localIdentifier =
                    uri.photoLibraryLocalIdentifier() ?: return@withContext error(
                        "Invalid photo library URI: $uri",
                    )
                val asset = fetchPhotoLibraryAsset(localIdentifier)
                return@withContext asset?.toPhotoLibraryMediaObject() ?: error("Unsupported or missing media at $uri")
            }

            val path = resolvePath(uri)
            val media = path?.let { toMediaObject(it) }
            return@withContext media ?: error("Unsupported or missing media at $uri")
        }

    override suspend fun exists(mediaId: String): Boolean =
        withContext(Dispatchers.Default) {
            if (mediaId.isPhotoLibraryUri()) {
                return@withContext fetchPhotoLibraryAsset(mediaId.photoLibraryLocalIdentifier()) != null
            }

            val path = resolvePath(mediaId)
            path != null && fileManager.fileExistsAtPath(path)
        }

    override suspend fun getRecentMedia(limit: Int): Flow<List<MediaObject>> =
        withContext(Dispatchers.Default) {
            val media =
                (listPhotoLibraryMedia(fetchLimit = MAX_RECENT_MEDIA) + listMediaObjects())
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
                (listPhotoLibraryMedia() + listMediaObjects())
                    .filter { it.timestamp >= start && it.timestamp < end }
            flowOf(media)
        }

    override suspend fun addToDefaultCollection(uri: String) =
        withContext(Dispatchers.Default) {
            if (uri.isPhotoLibraryUri()) {
                val payload =
                    runCatching { readPhotoLibraryPayload(uri.photoLibraryLocalIdentifier()) }
                        .getOrElse { error ->
                            Napier.e("Failed to copy photo-library media into app storage", error)
                            return@withContext
                        }

                ensureMediaDir()
                val targetName =
                    "${uri.photoLibraryLocalIdentifier()?.sanitizeForPath() ?: Uuid.random()}-" +
                        payload.fileName.sanitizeForPath()
                val destination = buildMediaPath(targetName)
                if (!fileManager.fileExistsAtPath(destination)) {
                    val data = payload.data.toNSData()
                    if (!data.writeToFile(destination, true)) {
                        Napier.e("Failed to write copied photo-library media to $destination")
                    }
                }
                return@withContext
            }

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
            if (uri.isPhotoLibraryUri()) {
                return@withContext readPhotoLibraryPayload(uri.photoLibraryLocalIdentifier())
            }

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

    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String =
        withContext(Dispatchers.Default) {
            ensureMediaDir()
            val sanitizedName = fileName.sanitizeForPath()
            val destFileName = "${Uuid.random()}-$sanitizedName"
            val destPath = buildMediaPath(destFileName)
            fileManager.copyItemAtPath(sourceFilePath, destPath, error = null)
            return@withContext NSURL.fileURLWithPath(destPath).absoluteString ?: "file://$destPath"
        }

    suspend fun resolvePhotoLibraryImageUri(localIdentifier: String): String? {
        val asset = fetchPhotoLibraryAsset(localIdentifier) ?: return null
        if (asset.mediaType != PHAssetMediaTypeImage) {
            return null
        }
        return resolveRenderableUri(asset)
    }

    suspend fun resolvePhotoLibraryVideoUri(localIdentifier: String): String? {
        val asset = fetchPhotoLibraryAsset(localIdentifier) ?: return null
        if (asset.mediaType != PHAssetMediaTypeVideo) {
            return null
        }
        return resolveRenderableUri(asset) ?: photoLibraryUri(localIdentifier)
    }

    private suspend fun listPhotoLibraryMedia(fetchLimit: Int? = null): List<MediaObject> {
        if (!hasPhotoLibraryReadAccess()) {
            return emptyList()
        }

        val options =
            PHFetchOptions().apply {
                sortDescriptors = listOf(platform.Foundation.NSSortDescriptor(key = "creationDate", ascending = false))
                if (fetchLimit != null) {
                    this.fetchLimit = fetchLimit.toULong()
                }
            }

        val results = PHAsset.fetchAssetsWithOptions(options)
        val count = results.count.toInt()

        return buildList(capacity = count.coerceAtMost(fetchLimit ?: count)) {
            for (index in 0 until count) {
                val asset = results.objectAtIndex(index.toULong()) as? PHAsset ?: continue
                asset.toPhotoLibraryMediaObject()?.let(::add)
            }
        }
    }

    private fun listMediaObjects(): List<MediaObject> {
        ensureMediaDir()
        val files = fileManager.contentsOfDirectoryAtPath(mediaRootPath, error = null) ?: return emptyList()
        return files
            .filterIsInstance<String>()
            .mapNotNull { fileName -> toMediaObject("$mediaRootPath/$fileName") }
    }

    private fun fetchPhotoLibraryAsset(localIdentifier: String?): PHAsset? {
        val identifier = localIdentifier?.takeIf { it.isNotBlank() } ?: return null
        if (!hasPhotoLibraryReadAccess()) {
            return null
        }

        val results = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(identifier), null)
        return results.firstObject as? PHAsset
    }

    private suspend fun PHAsset.toPhotoLibraryMediaObject(): MediaObject? {
        val timestamp =
            creationDate?.let { date ->
                Instant.fromEpochMilliseconds((date.timeIntervalSince1970 * 1000).toLong())
            } ?: Instant.fromEpochMilliseconds(0)

        val resource = preferredAssetResource(this)
        val fileName = resource?.originalFilename?.takeIf { it.isNotBlank() } ?: defaultPhotoLibraryFileName(this)

        return when (mediaType) {
            PHAssetMediaTypeImage -> {
                val uri = resolveRenderableUri(this) ?: return null
                MediaObject.Image(
                    uri = uri,
                    size = 0,
                    name = fileName,
                    timestamp = timestamp,
                )
            }
            PHAssetMediaTypeVideo -> {
                val uri = resolveRenderableUri(this) ?: photoLibraryUri(localIdentifier)
                MediaObject.Video(
                    name = fileName,
                    uri = uri,
                    size = 0,
                    timestamp = timestamp,
                    duration = (duration * 1000).toLong().milliseconds,
                )
            }
            PHAssetMediaTypeAudio -> null
            else -> null
        }
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

    private suspend fun readPhotoLibraryPayload(localIdentifier: String?): MediaPayload {
        val asset = fetchPhotoLibraryAsset(localIdentifier) ?: error("Photo-library asset not found: $localIdentifier")
        val resource = preferredAssetResource(asset) ?: error("Photo-library asset is missing a readable resource")
        val data = readAssetResource(resource)
        val fileName = resource.originalFilename.takeIf { it.isNotBlank() } ?: defaultPhotoLibraryFileName(asset)
        val mimeType = resource.uniformTypeIdentifier.toMimeType()

        return MediaPayload(
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = data.length.toLong(),
            data = data.toByteArray(),
        )
    }

    private suspend fun resolveRenderableUri(asset: PHAsset): String? =
        preferredAssetResource(asset)
            ?.let { resource -> exportResourceToCacheFile(asset, resource) }
            ?.absoluteString

    private suspend fun exportResourceToCacheFile(
        asset: PHAsset,
        resource: PHAssetResource,
    ): NSURL? {
        val cacheDir = ensurePhotoLibraryCacheDir()
        val fileName = "${asset.localIdentifier.sanitizeForPath()}-${resource.originalFilename.sanitizeForPath()}"
        val path = "$cacheDir/$fileName"
        if (fileManager.fileExistsAtPath(path)) {
            return NSURL.fileURLWithPath(path)
        }

        val data = readAssetResource(resource)
        if (!data.writeToFile(path, true)) {
            Napier.e("Failed to write renderable photo-library cache file to $path")
            return null
        }
        return NSURL.fileURLWithPath(path)
    }

    private suspend fun readAssetResource(resource: PHAssetResource): NSData =
        suspendCancellableCoroutine { continuation ->
            val chunks = mutableListOf<ByteArray>()
            val options =
                PHAssetResourceRequestOptions().apply {
                    networkAccessAllowed = true
                }

            PHAssetResourceManager
                .defaultManager()
                .requestDataForAssetResource(
                    resource = resource,
                    options = options,
                    dataReceivedHandler = { chunk ->
                        chunk?.let { chunks += it.toByteArray() }
                    },
                    completionHandler = { error ->
                        if (error != null) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException(error.localizedDescription),
                                )
                            }
                        } else if (continuation.isActive) {
                            continuation.resume(chunks.flattenToNSData())
                        }
                    },
                )
        }

    private fun preferredAssetResource(asset: PHAsset): PHAssetResource? {
        val resources =
            PHAssetResource
                .assetResourcesForAsset(asset)
                .filterIsInstance<PHAssetResource>()

        return when (asset.mediaType) {
            PHAssetMediaTypeImage ->
                resources.firstOrNull { it.type == PHAssetResourceTypeFullSizePhoto }
                    ?: resources.firstOrNull { it.type == PHAssetResourceTypePhoto }
                    ?: resources.firstOrNull()
            PHAssetMediaTypeVideo ->
                resources.firstOrNull { it.type == PHAssetResourceTypeFullSizeVideo }
                    ?: resources.firstOrNull { it.type == PHAssetResourceTypeVideo }
                    ?: resources.firstOrNull { it.type == PHAssetResourceTypePairedVideo }
                    ?: resources.firstOrNull()
            else -> resources.firstOrNull()
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
        if (uri.isPhotoLibraryUri()) {
            null
        } else if (uri.startsWith("file://")) {
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

    private fun ensurePhotoLibraryCacheDir(): String {
        val url: NSURL? =
            fileManager.URLForDirectory(
                directory = NSCachesDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )
        val basePath = requireNotNull(url?.path)
        val cachePath = "$basePath/$PHOTO_LIBRARY_CACHE_DIR_NAME"
        fileManager.createDirectoryAtPath(
            cachePath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return cachePath
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

    private fun List<ByteArray>.flattenToNSData(): NSData {
        if (isEmpty()) {
            return ByteArray(0).toNSData()
        }

        val totalSize = sumOf { it.size }
        val flattened = ByteArray(totalSize)
        var offset = 0
        for (chunk in this) {
            chunk.copyInto(flattened, destinationOffset = offset)
            offset += chunk.size
        }
        return flattened.toNSData()
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
        private const val PHOTO_LIBRARY_URI_PREFIX = "ph://"
        private const val PHOTO_LIBRARY_CACHE_DIR_NAME = "photo-library-renderable"
    }

    private fun hasPhotoLibraryReadAccess(): Boolean {
        val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
        return status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
    }

    private fun photoLibraryUri(localIdentifier: String): String = "$PHOTO_LIBRARY_URI_PREFIX$localIdentifier"

    private fun String.isPhotoLibraryUri(): Boolean = startsWith(PHOTO_LIBRARY_URI_PREFIX)

    private fun String.photoLibraryLocalIdentifier(): String? = removePrefix(PHOTO_LIBRARY_URI_PREFIX).takeIf { it.isNotBlank() }

    private fun String.sanitizeForPath(): String =
        replace("..", "_")
            .replace("/", "_")
            .replace("\\", "_")
            .replace(":", "_")

    private fun defaultPhotoLibraryFileName(asset: PHAsset): String {
        val baseName = asset.localIdentifier.sanitizeForPath()
        return when (asset.mediaType) {
            PHAssetMediaTypeImage -> "$baseName.jpg"
            PHAssetMediaTypeVideo -> "$baseName.mov"
            else -> baseName
        }
    }

    private fun String.toMimeType(): String =
        when (lowercase()) {
            "public.jpeg", "public.jpg" -> "image/jpeg"
            "public.png" -> "image/png"
            "com.compuserve.gif" -> "image/gif"
            "org.webmproject.webp" -> "image/webp"
            "public.heic", "public.heif" -> "image/heic"
            "com.apple.quicktime-movie" -> "video/quicktime"
            "public.mpeg-4" -> "video/mp4"
            else -> "application/octet-stream"
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
