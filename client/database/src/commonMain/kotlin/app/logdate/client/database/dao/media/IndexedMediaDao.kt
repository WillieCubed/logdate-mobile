package app.logdate.client.database.dao.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.logdate.client.database.entities.media.IndexedImageEntity
import app.logdate.client.database.entities.media.IndexedVideoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Data access object for indexed media.
 * 
 * This DAO handles the storage and retrieval of indexed media items, which are 
 * external media files (images, videos) that have been cataloged by the app.
 * Each media type has its own table with appropriate type-specific fields.
 */
@Dao
interface IndexedMediaDao {
    /**
     * Retrieves an indexed image by its unique identifier.
     * 
     * @param uid The unique identifier of the indexed image
     * @return Flow emitting the indexed image entity if found
     */
    @Query("SELECT * FROM indexed_media_images WHERE uid = :uid")
    fun getImageById(uid: Uuid): Flow<IndexedImageEntity?>
    
    /**
     * Retrieves an indexed video by its unique identifier.
     * 
     * @param uid The unique identifier of the indexed video
     * @return Flow emitting the indexed video entity if found
     */
    @Query("SELECT * FROM indexed_media_videos WHERE uid = :uid")
    fun getVideoById(uid: Uuid): Flow<IndexedVideoEntity?>
    
    /**
     * Retrieves an indexed image by its original URI.
     * 
     * @param uri The original URI of the image on the device
     * @return The indexed image entity if found, null otherwise
     */
    @Query("SELECT * FROM indexed_media_images WHERE uri = :uri LIMIT 1")
    suspend fun getImageByUri(uri: String): IndexedImageEntity?
    
    /**
     * Retrieves an indexed video by its original URI.
     * 
     * @param uri The original URI of the video on the device
     * @return The indexed video entity if found, null otherwise
     */
    @Query("SELECT * FROM indexed_media_videos WHERE uri = :uri LIMIT 1")
    suspend fun getVideoByUri(uri: String): IndexedVideoEntity?
    
    /**
     * Checks if an image with the given URI is already indexed.
     * 
     * @param uri The original URI of the image on the device
     * @return True if the image is indexed, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM indexed_media_images WHERE uri = :uri)")
    suspend fun isImageIndexed(uri: String): Boolean
    
    /**
     * Checks if a video with the given URI is already indexed.
     * 
     * @param uri The original URI of the video on the device
     * @return True if the video is indexed, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM indexed_media_videos WHERE uri = :uri)")
    suspend fun isVideoIndexed(uri: String): Boolean
    
    /**
     * Checks if any media with the given URI is already indexed.
     * 
     * @param uri The original URI of the media on the device
     * @return True if any media is indexed with this URI, false otherwise
     */
    @Transaction
    suspend fun isMediaIndexed(uri: String): Boolean {
        return isImageIndexed(uri) || isVideoIndexed(uri)
    }
    
    /**
     * Retrieves all indexed image items created within a time period.
     * 
     * @param start The start of the time period (inclusive)
     * @param end The end of the time period (exclusive)
     * @return Flow emitting a list of indexed image entities
     */
    @Query("SELECT * FROM indexed_media_images WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getImagesForPeriod(start: Instant, end: Instant): Flow<List<IndexedImageEntity>>
    
    /**
     * Retrieves all indexed video items created within a time period.
     * 
     * @param start The start of the time period (inclusive)
     * @param end The end of the time period (exclusive)
     * @return Flow emitting a list of indexed video entities
     */
    @Query("SELECT * FROM indexed_media_videos WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun getVideosForPeriod(start: Instant, end: Instant): Flow<List<IndexedVideoEntity>>
    
    /**
     * Retrieves all indexed image media items.
     * 
     * @return Flow emitting a list of indexed image entities
     */
    @Query("SELECT * FROM indexed_media_images")
    fun getAllIndexedImages(): Flow<List<IndexedImageEntity>>
    
    /**
     * Retrieves all indexed video media items.
     * 
     * @return Flow emitting a list of indexed video entities
     */
    @Query("SELECT * FROM indexed_media_videos")
    fun getAllIndexedVideos(): Flow<List<IndexedVideoEntity>>
    
    /**
     * Inserts a new indexed image entity.
     * 
     * @param image The indexed image entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: IndexedImageEntity)
    
    /**
     * Inserts a new indexed video entity.
     * 
     * @param video The indexed video entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: IndexedVideoEntity)
    
    /**
     * Removes an indexed image item.
     * 
     * @param uid The unique identifier of the indexed image to remove
     * @return The number of rows deleted
     */
    @Query("DELETE FROM indexed_media_images WHERE uid = :uid")
    suspend fun removeImage(uid: Uuid): Int
    
    /**
     * Removes an indexed video item.
     * 
     * @param uid The unique identifier of the indexed video to remove
     * @return The number of rows deleted
     */
    @Query("DELETE FROM indexed_media_videos WHERE uid = :uid")
    suspend fun removeVideo(uid: Uuid): Int
    
    /**
     * Retrieves all media for a time period as a combined result.
     * 
     * @param start The start of the time period (inclusive)
     * @param end The end of the time period (exclusive)
     * @return Container with lists of images and videos
     */
    @Transaction
    suspend fun getAllMediaForPeriod(start: Instant, end: Instant): IndexedMediaContent {
        val images = getImagesForPeriod(start, end).firstOrNull() ?: emptyList()
        val videos = getVideosForPeriod(start, end).firstOrNull() ?: emptyList()
        return IndexedMediaContent(images, videos)
    }
}

/**
 * Container for holding collections of different media types.
 * 
 * This class is used to return multiple collections of different
 * media types from a single transaction.
 */
data class IndexedMediaContent(
    val images: List<IndexedImageEntity>,
    val videos: List<IndexedVideoEntity>
)