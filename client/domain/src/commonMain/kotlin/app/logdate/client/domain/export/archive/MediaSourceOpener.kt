package app.logdate.client.domain.export.archive

import okio.Source

/**
 * Opens a media file by the reference the app stored for it.
 *
 * A reference is whatever the platform saved: a content URI, an absolute path, a photo library id.
 * How to open each kind is platform knowledge, so each platform supplies its own opener; the export
 * only asks for bytes and never writes the reference into an archive.
 */
fun interface MediaSourceOpener {
    /** A source for [reference] that the caller must close, or null when the file cannot be found. */
    suspend fun open(reference: String): Source?
}
