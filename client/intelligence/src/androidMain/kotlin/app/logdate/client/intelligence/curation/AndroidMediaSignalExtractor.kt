package app.logdate.client.intelligence.curation

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import app.logdate.client.repository.media.IndexedMedia
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * Android signal extractor backed by `MediaStore`.
 *
 * Reads dimensions, MIME type, file name, size, and folder for every media URI in the
 * input. Screenshots are flagged when the photo lives in the `Screenshots` folder or
 * the filename starts with `Screenshot_`. Doc scans are flagged by filename pattern
 * (CamScanner / Adobe Scan / generic `Scan_`) or an extreme aspect ratio.
 *
 * EXIF GPS / camera metadata is intentionally out of scope here — the location-novelty
 * scorer doesn't use it on Android today, and adding `androidx.exifinterface` belongs
 * with the broader photo-EXIF work.
 */
class AndroidMediaSignalExtractor(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaSignalExtractor {
    override suspend fun extract(media: List<IndexedMedia>): Map<Uuid, MediaSignals> =
        withContext(ioDispatcher) {
            media.associate { it.uid to extractOne(it) }
        }

    private fun extractOne(item: IndexedMedia): MediaSignals {
        // Prefer signals already captured on the indexed media row — avoids a MediaStore
        // round-trip per item. We still always query MediaStore for the folder / file
        // name signals (those aren't cached on IndexedMedia), but skip the columns we
        // already have on hand.
        val cachedWidth =
            (item as? IndexedMedia.Image)?.widthPx
                ?: (item as? IndexedMedia.Video)?.widthPx
        val cachedHeight =
            (item as? IndexedMedia.Image)?.heightPx
                ?: (item as? IndexedMedia.Video)?.heightPx
        val cachedMime =
            (item as? IndexedMedia.Image)?.mimeType
                ?: (item as? IndexedMedia.Video)?.mimeType

        val uri =
            runCatching { Uri.parse(item.uri) }
                .getOrElse {
                    Napier.w("AndroidMediaSignalExtractor: unparseable URI ${item.uri}", it)
                    return MediaSignals(widthPx = cachedWidth, heightPx = cachedHeight, mimeType = cachedMime)
                }

        val cursorSignals =
            readCursorRow(uri, item is IndexedMedia.Video)
                ?: return MediaSignals(widthPx = cachedWidth, heightPx = cachedHeight, mimeType = cachedMime)
        val nameLower = cursorSignals.fileName?.lowercase()
        val bucketLower = cursorSignals.bucketDisplayName?.lowercase()

        val isScreenshot =
            bucketLower == BUCKET_SCREENSHOTS ||
                nameLower?.startsWith("screenshot_") == true ||
                nameLower?.startsWith("screen shot") == true

        val isDocScan =
            nameLower != null &&
                (
                    nameLower.startsWith("scan_") ||
                        nameLower.startsWith("camscanner") ||
                        nameLower.startsWith("adobe scan") ||
                        nameLower.contains("_scan_") ||
                        isExtremeAspectRatio(cursorSignals.widthPx, cursorSignals.heightPx)
                )

        return MediaSignals(
            widthPx = cachedWidth ?: cursorSignals.widthPx,
            heightPx = cachedHeight ?: cursorSignals.heightPx,
            mimeType = cachedMime ?: cursorSignals.mimeType,
            fileName = cursorSignals.fileName,
            sizeBytes = cursorSignals.sizeBytes,
            isLikelyScreenshot = isScreenshot,
            isLikelyDocumentScan = isDocScan,
            // Burst grouping stays timestamp-driven in [PhotoHardFilter] — MediaStore on
            // Android doesn't expose a stable burst id, so we don't surface one here.
            isLikelyBurstMember = false,
            burstGroupKey = null,
        )
    }

    /**
     * One row of MediaStore columns we care about for curation. Returns null when the
     * row is missing or the cursor fails — the curator degrades gracefully.
     */
    private fun readCursorRow(
        uri: Uri,
        isVideo: Boolean,
    ): CursorSignals? {
        val projection =
            arrayOf(
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            )
        return runCatching {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val widthIdx = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightIdx = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val bucketIdx = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                CursorSignals(
                    widthPx = widthIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getInt(it) },
                    heightPx = heightIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getInt(it) },
                    mimeType = mimeIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getString(it) },
                    fileName = nameIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getString(it) },
                    sizeBytes = sizeIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getLong(it) },
                    bucketDisplayName = bucketIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getString(it) },
                )
            }
        }.onFailure {
            Napier.w("AndroidMediaSignalExtractor: MediaStore query failed for $uri (isVideo=$isVideo)", it)
        }.getOrNull()
    }

    private fun isExtremeAspectRatio(
        width: Int?,
        height: Int?,
    ): Boolean {
        if (width == null || height == null || width == 0 || height == 0) return false
        val ratio = width.toDouble() / height.toDouble()
        return ratio > 2.0 || ratio < 0.5
    }

    private data class CursorSignals(
        val widthPx: Int?,
        val heightPx: Int?,
        val mimeType: String?,
        val fileName: String?,
        val sizeBytes: Long?,
        val bucketDisplayName: String?,
    )

    private companion object {
        private const val BUCKET_SCREENSHOTS = "screenshots"
    }
}
