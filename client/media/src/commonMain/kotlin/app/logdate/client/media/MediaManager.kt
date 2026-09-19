package app.logdate.client.media

import kotlinx.coroutines.flow.Flow
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlin.time.Duration
import kotlin.time.Instant

interface MediaManager {
    /**
     * Retrieves a media object with the given [uri].
     *
     * If the media object does not exist, an exception will be thrown.
     */
    suspend fun getMedia(uri: String): MediaObject

    /**
     * Checks if a media object with the given [mediaId] exists.
     */
    suspend fun exists(mediaId: String): Boolean

    /**
     * Deletes media that LogDate itself stored, and reports whether anything was removed.
     *
     * Only LogDate's own copies are eligible. A [uri] that points at something the user owns --
     * a photo in their gallery, a file they picked -- is left alone and `false` is returned:
     * removing an entry from a journal must never remove the original from the device.
     *
     * Callers are responsible for establishing that nothing else references the media. Media is
     * stored content-addressed, so two entries holding identical bytes share one file.
     */
    suspend fun deleteOwnedMedia(uri: String): Boolean

    /**
     * Retrieves the most recent media objects.
     *
     * This consists of the most recent images and videos currently available from the
     * platform media library or photo storage source.
     *
     * @param limit Maximum number of items to materialize per media type (images and videos
     *   each cap at this value). Implementations should push the limit into the underlying
     *   query, not materialize everything and then slice. Default sized so the in-app picker
     *   strip never starves while keeping memory bounded on huge libraries.
     */
    suspend fun getRecentMedia(limit: Int = DEFAULT_RECENT_MEDIA_LIMIT): Flow<List<MediaObject>>

    /**
     * Retrieves all media objects between the given [start] and [end] timestamps.
     *
     * @param start The start timestamp (inclusive)
     * @param end The end timestamp (exclusive)
     */
    suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>>

    /**
     * Adds the media object with the given [uri] to the default collection.
     *
     * If the media object already exists in the default collection, it will not be added again.
     *
     * @param uri The URI of the on-device media object to add to the default collection
     */
    suspend fun addToDefaultCollection(uri: String)

    /**
     * Reads a media asset into memory for upload or processing.
     *
     * @param uri The URI of the media asset to read
     * @return The media payload including bytes and metadata
     */
    suspend fun readMedia(uri: String): MediaPayload

    /**
     * Opens a media asset for reading without loading it into memory.
     *
     * Prefer this over [readMedia] for uploads: a recording can be tens of megabytes, and
     * [MediaFileSource.open] reads it from disk in small chunks as it is consumed. The default
     * implementation falls back to [readMedia] and holds the bytes in memory; platforms that
     * store media as files override it.
     *
     * @param uri The URI of the media asset to open
     * @return The asset's metadata and a way to read its bytes, as many times as needed
     */
    suspend fun openMedia(uri: String): MediaFileSource {
        val payload = readMedia(uri)
        return MediaFileSource(
            fileName = payload.fileName,
            mimeType = payload.mimeType,
            sizeBytes = payload.data.size.toLong(),
        ) { ByteArrayRawSource(payload.data) }
    }

    /**
     * Saves a media payload to local storage.
     *
     * @param payload The media payload to persist
     * @return A URI pointing to the saved media asset
     */
    suspend fun saveMedia(payload: MediaPayload): String

    /**
     * Saves a media file already on disk to managed storage.
     *
     * Preferred over [saveMedia] for large files since the platform
     * implementation can move or copy the file without loading it
     * entirely into memory.
     *
     * @param sourceFilePath Absolute path to the source file on disk
     * @param fileName Display name for the saved file
     * @param mimeType MIME type of the media
     * @return A URI pointing to the saved media asset
     */
    suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String
}

const val DEFAULT_RECENT_MEDIA_LIMIT: Int = 100

data class MediaPayload(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray,
)

/**
 * A media asset that is read from storage only as it is consumed.
 *
 * [open] returns a fresh source positioned at the first byte on every call, so a caller that
 * needs to read the asset more than once (for example to peek at a header and then upload it)
 * opens it again rather than buffering it. The caller closes each source it opens.
 */
class MediaFileSource(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    private val openSource: () -> RawSource,
) {
    fun open(): RawSource = openSource()
}

sealed interface MediaObject {
    // TODO: Support multiplatform URI

    /**
     * The URI of this media object.
     */
    val uri: String

    /**
     * The file name of this media object.
     */
    val name: String

    /**
     * The size of the media object in bytes.
     */
    val size: Int

    val timestamp: Instant

    data class Image(
        override val uri: String,
        override val size: Int,
        override val name: String,
        override val timestamp: Instant,
    ) : MediaObject

    data class Video(
        override val name: String,
        override val uri: String,
        override val size: Int,
        override val timestamp: Instant,
        /**
         * The duration of the video.
         */
        val duration: Duration,
    ) : MediaObject
}

/** Reads [data] in place, so reopening an in-memory payload never copies all of it. */
private class ByteArrayRawSource(
    private val data: ByteArray,
) : RawSource {
    private var position = 0

    override fun readAtMostTo(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        if (position == data.size) return -1
        val end = position + minOf(byteCount, (data.size - position).toLong()).toInt()
        sink.write(data, position, end)
        val read = end - position
        position = end
        return read.toLong()
    }

    override fun close() = Unit
}
