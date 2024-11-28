package app.logdate.client.media

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

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
     * Retrieves the most recent media objects.
     *
     * This consists of all images and videos that have been added to the app recently.
     */
    suspend fun getRecentMedia(): Flow<List<MediaObject>>

    /**
     * Retrieves all media objects between the given [start] and [end] timestamps.
     *
     * @param start The start timestamp (inclusive)
     * @param end The end timestamp (exclusive)
     */
    suspend fun queryMediaByDate(start: Instant, end: Instant): Flow<List<MediaObject>>

    /**
     * Adds the media object with the given [uri] to the default collection.
     *
     * If the media object already exists in the default collection, it will not be added again.
     *
     * @param uri The URI of the on-device media object to add to the default collection
     */
    suspend fun addToDefaultCollection(uri: String)
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
         * The duration of the video in milliseconds.
         */
        val duration: Long,
    ) : MediaObject
}
