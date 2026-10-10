package app.logdate.client.media.storage

/**
 * Turns any spelling of a media reference into the one LogDate stores.
 *
 * Repositories call this on every media reference they persist and on every reference they
 * compare, so two spellings of one file are always recognised as the same file. Each platform
 * provides it from its [MediaFileResolver]; see `docs/reference/media-references.md`.
 */
fun interface StoredMediaReferences {
    fun storedReference(reference: String): String

    companion object {
        /** Stores every reference as written, for code that has no local media to resolve. */
        val Unchanged: StoredMediaReferences = StoredMediaReferences { it }
    }
}
