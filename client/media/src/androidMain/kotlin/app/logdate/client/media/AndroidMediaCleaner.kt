package app.logdate.client.media

import app.logdate.client.media.storage.MediaFileResolver

/**
 * Android filesystem-backed [MediaCleaner].
 *
 * A draft's stale reference check cannot establish exclusive ownership across notes, covers,
 * other drafts, and concurrent writes. Keep persisted files until reference-aware garbage
 * collection can safely reclaim them.
 */
@Suppress("UNUSED_PARAMETER")
class AndroidMediaCleaner(
    mediaFiles: MediaFileResolver,
) : MediaCleaner {
    override suspend fun delete(path: String) = Unit
}
