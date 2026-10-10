package app.logdate.client.media.storage

/**
 * Moves media a note stores outside LogDate's own collections into them, so it survives the things
 * that clear or skip those places (a cache purge, a restore from a backup).
 *
 * The stored-reference migration asks it about every reference that is still a local path after
 * [StoredMediaReferences] has had its turn. It returns the reference to store instead, or `null`
 * when the media is not its to move or is no longer there.
 */
fun interface MediaRescuer {
    suspend fun rescue(reference: String): String?

    companion object {
        /** Rescues nothing, for platforms that never stored media outside the collections. */
        val None: MediaRescuer = MediaRescuer { null }
    }
}
