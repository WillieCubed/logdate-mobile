package app.logdate.client.repository.media

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Repository for managing indexed media within the app.
 * 
 * This repository manages media that has been explicitly indexed by the app,
 * either through user interaction or automated processes. Indexed media is media that
 * has been identified, cataloged, and made available for use within the app ecosystem.
 * 
 * Key concepts:
 * - Media indexing: The process of registering external media (from device storage) into the app's managed collection
 * - Media references: After indexing, all references to media should use the assigned UIDs rather than external URIs
 * - Persistence: Indexed media metadata is stored within the app's database for quick retrieval
 */
interface IndexedMediaRepository {
    /**
     * Indexes an image from the device and adds it to the app's media collection.
     * 
     * This takes an external image URI and creates a persistent reference within the app,
     * assigning it a unique identifier (UID) and capturing relevant metadata.
     * 
     * @param uri The URI of the image on the device (e.g., from MediaStore on Android)
     * @param timestamp When the image was created
     * @return The indexed media item with its assigned UID
     * @throws IllegalStateException if the media cannot be accessed or indexed
     */
    suspend fun indexImage(uri: String, timestamp: Instant): IndexedMedia.Image
    
    /**
     * Indexes a video from the device and adds it to the app's media collection.
     * 
     * This takes an external video URI and creates a persistent reference within the app,
     * assigning it a unique identifier (UID) and capturing relevant metadata.
     * 
     * @param uri The URI of the video on the device (e.g., from MediaStore on Android)
     * @param timestamp When the video was created
     * @param duration Duration of the video
     * @return The indexed media item with its assigned UID
     * @throws IllegalStateException if the media cannot be accessed or indexed
     */
    suspend fun indexVideo(uri: String, timestamp: Instant, duration: Duration): IndexedMedia.Video
    
    /**
     * Retrieves an indexed media item by its unique identifier.
     * 
     * @param uid The unique identifier of the indexed media
     * @return The indexed media item, or null if not found
     */
    suspend fun getByUid(uid: Uuid): IndexedMedia?
    
    /**
     * Gets all indexed media items created within a specified time period.
     * 
     * This is particularly useful for features like Rewinds that need to collect
     * all relevant media for a specific timeframe.
     * 
     * @param startTime Start of the time period (inclusive)
     * @param endTime End of the time period (exclusive)
     * @return Flow emitting the list of indexed media in the period, ordered by timestamp
     */
    fun getForPeriod(startTime: Instant, endTime: Instant): Flow<List<IndexedMedia>>
    
    /**
     * Checks if a device media item is already indexed within the app.
     * 
     * This prevents duplicate indexing of the same media item and can be used
     * to determine if a media item needs to be indexed before use.
     * 
     * @param uri The URI of the media on the device
     * @return True if the media is already indexed, false otherwise
     */
    suspend fun isIndexed(uri: String): Boolean
    
    /**
     * Removes an indexed media item from the app's collection.
     * 
     * This does not delete the original media from the device, only the app's reference to it.
     * Note that if the media is referenced by other content (e.g., journal entries), this
     * operation might be restricted.
     * 
     * @param uid The unique identifier of the indexed media to remove
     * @return True if the media was successfully removed, false otherwise
     */
    suspend fun remove(uid: Uuid): Boolean
    
    /**
     * Updates the caption for an indexed media item.
     * 
     * Captions provide context and description for media items and can enhance
     * the user experience when viewing media in Rewinds and other features.
     * 
     * @param uid The unique identifier of the indexed media
     * @param caption The new caption, or null to remove the caption
     * @return The updated indexed media item, or null if the media wasn't found
     */
    suspend fun updateCaption(uid: Uuid, caption: String?): IndexedMedia?
}

/**
 * Represents a media item that has been indexed by the app.
 * 
 * Indexed media items are external media (photos, videos) that have been cataloged
 * within the app ecosystem. Each indexed item has a unique identifier and relevant
 * metadata that enables efficient retrieval and usage within the app.
 */
sealed class IndexedMedia {
    /**
     * Unique identifier for this indexed media.
     * 
     * This UID should be used for all references to this media within the app,
     * rather than the external URI, to maintain consistency even if the
     * underlying media source changes.
     */
    abstract val uid: Uuid
    
    /**
     * URI of the original media on the device.
     * 
     * This points to the actual media file in the device's storage system.
     * On Android, this would typically be a MediaStore URI.
     */
    abstract val uri: String
    
    /**
     * When the media was created.
     * 
     * This timestamp is used for chronological ordering and time-based filtering
     * of media items, which is essential for features like Rewinds.
     */
    abstract val timestamp: Instant
    
    /**
     * Optional caption for the media.
     * 
     * Captions provide context and description for media items, enhancing
     * the user experience when viewing media in various app features.
     */
    abstract val caption: String?
    
    /**
     * An indexed image.
     * 
     * Represents a still image that has been indexed by the app.
     * 
     * @param uid Unique identifier for this indexed image
     * @param uri URI of the original image on the device
     * @param timestamp When the image was created
     * @param caption Optional caption for the image
     */
    data class Image(
        override val uid: Uuid,
        override val uri: String,
        override val timestamp: Instant,
        override val caption: String? = null
    ) : IndexedMedia()
    
    /**
     * An indexed video.
     * 
     * Represents a video recording that has been indexed by the app.
     * 
     * @param uid Unique identifier for this indexed video
     * @param uri URI of the original video on the device
     * @param timestamp When the video was created
     * @param caption Optional caption for the video
     * @param duration Length of the video
     */
    data class Video(
        override val uid: Uuid,
        override val uri: String,
        override val timestamp: Instant,
        override val caption: String? = null,
        val duration: Duration
    ) : IndexedMedia()
}