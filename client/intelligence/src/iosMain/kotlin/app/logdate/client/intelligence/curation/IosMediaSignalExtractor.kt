package app.logdate.client.intelligence.curation

import app.logdate.client.repository.media.IndexedMedia
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaSubtypePhotoScreenshot
import kotlin.uuid.Uuid

/**
 * iOS signal extractor backed by `PHAsset` metadata.
 *
 * Reads pixel dimensions, screenshot flag, burst identifier, and EXIF location from the
 * Photos library for every IndexedMedia URI (which on iOS is a `PHAsset.localIdentifier`).
 * Media kind is inferred from cached image/video typing on the IndexedMedia row — no need
 * to query the asset to learn it.
 *
 * Returns an all-nulls [MediaSignals] when the URI doesn't resolve to a fetchable asset.
 */
class IosMediaSignalExtractor : MediaSignalExtractor {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun extract(media: List<IndexedMedia>): Map<Uuid, MediaSignals> {
        if (media.isEmpty()) return emptyMap()
        val cachedById = media.associateBy { it.uid }

        // Fetch all assets in one batch.
        val identifiers = media.map { it.uri }
        val fetchResult =
            runCatching { PHAsset.fetchAssetsWithLocalIdentifiers(identifiers, null) }
                .getOrElse {
                    Napier.w("IosMediaSignalExtractor: fetch failed", it)
                    return media.associate { it.uid to MediaSignals() }
                }
        val assetById = mutableMapOf<String, PHAsset>()
        val count = fetchResult.count.toInt()
        for (index in 0 until count) {
            val asset = fetchResult.objectAtIndex(index.toULong()) as? PHAsset ?: continue
            assetById[asset.localIdentifier] = asset
        }

        return media.associate { item ->
            val asset = assetById[item.uri]
            val signals =
                if (asset == null) {
                    MediaSignals(
                        widthPx = cachedWidth(item),
                        heightPx = cachedHeight(item),
                        mimeType = cachedMime(item),
                    )
                } else {
                    val isScreenshot =
                        asset.mediaSubtypes and PHAssetMediaSubtypePhotoScreenshot ==
                            PHAssetMediaSubtypePhotoScreenshot
                    val (lat, lon) = asset.location?.coordinatesOrNull() ?: (null to null)
                    MediaSignals(
                        widthPx = cachedWidth(item) ?: asset.pixelWidth.toInt(),
                        heightPx = cachedHeight(item) ?: asset.pixelHeight.toInt(),
                        mimeType = cachedMime(item),
                        fileName = null,
                        sizeBytes = null,
                        isLikelyScreenshot = isScreenshot,
                        isLikelyDocumentScan = false,
                        isLikelyBurstMember = asset.burstIdentifier != null,
                        burstGroupKey = asset.burstIdentifier,
                        latitude = lat,
                        longitude = lon,
                    )
                }
            cachedById.getValue(item.uid).uid to signals
        }
    }

    private fun cachedWidth(item: IndexedMedia): Int? = (item as? IndexedMedia.Image)?.widthPx ?: (item as? IndexedMedia.Video)?.widthPx

    private fun cachedHeight(item: IndexedMedia): Int? = (item as? IndexedMedia.Image)?.heightPx ?: (item as? IndexedMedia.Video)?.heightPx

    private fun cachedMime(item: IndexedMedia): String? = (item as? IndexedMedia.Image)?.mimeType ?: (item as? IndexedMedia.Video)?.mimeType
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.coordinatesOrNull(): Pair<Double, Double>? =
    runCatching {
        coordinate.useContents { latitude to longitude }
    }.getOrNull()
