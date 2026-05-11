package app.logdate.client.intelligence.curation

import app.logdate.client.repository.media.IndexedMedia
import kotlin.uuid.Uuid

/**
 * Platform-specific extractor that derives [MediaSignals] from indexed media.
 *
 * Implementations read MediaStore columns / `PHAsset` properties / image-header bytes to
 * surface dimensions, MIME type, screenshot / doc-scan / burst flags, and EXIF location.
 * The cross-platform fallback returns all-nulls — the scorer treats null signals as 0
 * contributions, so the curation pass still runs, just with less differentiation.
 */
interface MediaSignalExtractor {
    /**
     * Extracts signals for every item in [media]. Suspend because some platforms must
     * hop off the main thread for storage queries.
     */
    suspend fun extract(media: List<IndexedMedia>): Map<Uuid, MediaSignals>
}

/**
 * Skeleton extractor that returns an all-nulls signal map for every item. Used as the
 * default on platforms that don't yet have a real implementation, and in tests where the
 * cross-platform signals (people / journal / time clustering) are what's being verified.
 */
class NoSignalsExtractor : MediaSignalExtractor {
    override suspend fun extract(media: List<IndexedMedia>): Map<Uuid, MediaSignals> = media.associate { it.uid to MediaSignals() }
}
