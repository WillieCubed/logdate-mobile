package app.logdate.client.intelligence.curation

import app.logdate.client.repository.media.IndexedMedia
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import kotlin.uuid.Uuid

/**
 * Desktop signal extractor backed by `javax.imageio.ImageIO` for image-header reads.
 *
 * Pulls width / height for image and video URIs, infers MIME from file extension, and
 * applies filename heuristics for screenshot detection ("Screenshot", "Screen Shot",
 * "CleanShot") and document scans ("Scan_", "CamScanner", "Adobe Scan"). Burst grouping
 * is not a desktop concept, and EXIF GPS is deferred until a desktop EXIF library lands.
 *
 * Returns an all-nulls [MediaSignals] when the URI can't be parsed or the file can't be
 * read — the scorer treats null fields as zero contributions, so curation still runs.
 */
class DesktopMediaSignalExtractor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaSignalExtractor {
    override suspend fun extract(media: List<IndexedMedia>): Map<Uuid, MediaSignals> =
        withContext(ioDispatcher) {
            media.associate { it.uid to extractOne(it) }
        }

    private fun extractOne(item: IndexedMedia): MediaSignals {
        val cachedWidth =
            (item as? IndexedMedia.Image)?.widthPx
                ?: (item as? IndexedMedia.Video)?.widthPx
        val cachedHeight =
            (item as? IndexedMedia.Image)?.heightPx
                ?: (item as? IndexedMedia.Video)?.heightPx
        val cachedMime =
            (item as? IndexedMedia.Image)?.mimeType
                ?: (item as? IndexedMedia.Video)?.mimeType

        val file = uriToFile(item.uri)
        val fileName = file?.name
        val nameLower = fileName?.lowercase()
        val isLikelyScreenshot = nameLower != null && SCREENSHOT_NAME_PATTERNS.any { nameLower.contains(it) }
        val isLikelyDocumentScan = nameLower != null && DOC_SCAN_NAME_PATTERNS.any { nameLower.contains(it) }

        val (width, height, mime) =
            when (item) {
                is IndexedMedia.Image -> readImageHeader(file, cachedWidth, cachedHeight, cachedMime, fileName)
                is IndexedMedia.Video -> Triple(cachedWidth, cachedHeight, cachedMime ?: mimeFromName(fileName))
            }

        val aspectRatio =
            if (width != null && height != null && width > 0 && height > 0) {
                width.toDouble() / height.toDouble()
            } else {
                null
            }
        val extremeAspect =
            aspectRatio != null &&
                (aspectRatio < DOC_SCAN_ASPECT_MIN || aspectRatio > DOC_SCAN_ASPECT_MAX)

        return MediaSignals(
            widthPx = width,
            heightPx = height,
            mimeType = mime,
            fileName = fileName,
            sizeBytes = file?.length(),
            isLikelyScreenshot = isLikelyScreenshot,
            isLikelyDocumentScan = isLikelyDocumentScan || extremeAspect,
            isLikelyBurstMember = false,
            burstGroupKey = null,
            latitude = null,
            longitude = null,
            cameraMake = null,
            cameraModel = null,
        )
    }

    private fun readImageHeader(
        file: File?,
        cachedWidth: Int?,
        cachedHeight: Int?,
        cachedMime: String?,
        fileName: String?,
    ): Triple<Int?, Int?, String?> {
        if (file == null || !file.exists() || !file.isFile) {
            return Triple(cachedWidth, cachedHeight, cachedMime ?: mimeFromName(fileName))
        }
        if (cachedWidth != null && cachedHeight != null && cachedMime != null) {
            return Triple(cachedWidth, cachedHeight, cachedMime)
        }
        return runCatching {
            ImageIO.createImageInputStream(file).use { stream ->
                val readers = ImageIO.getImageReaders(stream)
                if (!readers.hasNext()) {
                    return Triple(cachedWidth, cachedHeight, cachedMime ?: mimeFromName(fileName))
                }
                val reader = readers.next()
                reader.input = stream
                val width = cachedWidth ?: reader.getWidth(0)
                val height = cachedHeight ?: reader.getHeight(0)
                val mime = cachedMime ?: reader.originatingProvider.mimeTypes.firstOrNull() ?: mimeFromName(fileName)
                reader.dispose()
                Triple(width, height, mime)
            }
        }.getOrElse {
            Napier.w("DesktopMediaSignalExtractor: header read failed for ${file.path}", it)
            Triple(cachedWidth, cachedHeight, cachedMime ?: mimeFromName(fileName))
        }
    }

    private fun uriToFile(uri: String): File? =
        runCatching {
            val parsed = URI(uri)
            when {
                parsed.scheme == "file" -> File(parsed)
                parsed.scheme == null -> File(uri)
                else -> null
            }
        }.getOrNull()

    private fun mimeFromName(fileName: String?): String? {
        if (fileName == null) return null
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic", "heif" -> "image/heic"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> null
        }
    }

    private companion object {
        val SCREENSHOT_NAME_PATTERNS: List<String> =
            listOf("screenshot", "screen shot", "screen_shot", "cleanshot")
        val DOC_SCAN_NAME_PATTERNS: List<String> =
            listOf("scan_", "camscanner", "adobe scan", "adobescan")
        const val DOC_SCAN_ASPECT_MIN: Double = 0.55
        const val DOC_SCAN_ASPECT_MAX: Double = 1.85
    }
}
