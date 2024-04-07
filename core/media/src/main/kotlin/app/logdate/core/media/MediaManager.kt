package app.logdate.core.media

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface MediaManager {
    /**
     * Retrieves a media object with the given [uri].
     *
     * If the media object does not exist, an exception will be thrown.
     */
    suspend fun getMedia(uri: Uri): MediaObject

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
}

sealed class MediaObject {
    /**
     * The URI of this media object.
     */
    abstract val uri: Uri

    /**
     * The file name of this media object.
     */
    abstract val name: String

    /**
     * The size of the media object in bytes.
     */
    abstract val size: Int

    data class Image(
        override val uri: Uri,
        override val size: Int,
        override val name: String,
    ) : MediaObject()

    data class Video(
        override val name: String,
        override val uri: Uri,
        override val size: Int,
        /**
         * The duration of the video in milliseconds.
         */
        val duration: Long,
    ) : MediaObject()
}
