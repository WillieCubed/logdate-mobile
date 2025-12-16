package app.logdate.client.data.media

import app.logdate.client.database.dao.media.IndexedMediaDao
import app.logdate.client.database.entities.media.IndexedImageEntity
import app.logdate.client.database.entities.media.IndexedVideoEntity
import app.logdate.client.database.entities.media.MediaDimensions
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.media.IndexedMediaRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Offline-first implementation of [IndexedMediaRepository] that stores data in a local database.
 *
 * This repository allows the application to index and retrieve external media files (images and videos)
 * so they can be included in journals, rewinds, and other features.
 */
class OfflineIndexedMediaRepository(
    private val indexedMediaDao: IndexedMediaDao,
) : IndexedMediaRepository {

    override suspend fun indexImage(uri: String, timestamp: Instant): IndexedMedia.Image {
        // Check if already indexed
        if (indexedMediaDao.isImageIndexed(uri)) {
            Napier.d("Image already indexed: $uri")
            val existing = indexedMediaDao.getImageByUri(uri)
            if (existing != null) {
                return mapToIndexedImage(existing)
            }
            // If we got here, something went wrong with the existing image - proceed to reindex
        }
        
        try {
            val uid = Uuid.random()
            
            // For a basic implementation, we'll create an image entity with default values
            // In a real implementation, we'd extract metadata from the actual image file
            val imageEntity = IndexedImageEntity(
                uid = uid,
                uri = uri,
                timestamp = timestamp,
                indexedAt = Clock.System.now(),
                mimeType = "image/jpeg",  // Assumed default
                fileSize = 0,  // Default value
                dimensions = MediaDimensions(
                    width = 0,  // Default value
                    height = 0  // Default value
                ),
                location = null
            )
            
            indexedMediaDao.insertImage(imageEntity)
            
            return IndexedMedia.Image(
                uid = uid,
                uri = uri,
                timestamp = timestamp,
                caption = null
            )
        } catch (e: Exception) {
            Napier.e("Failed to index image", e)
            throw IllegalStateException("Failed to index image: ${e.message}", e)
        }
    }

    override suspend fun indexVideo(uri: String, timestamp: Instant, duration: Duration): IndexedMedia.Video {
        // Check if already indexed
        if (indexedMediaDao.isVideoIndexed(uri)) {
            Napier.d("Video already indexed: $uri")
            val existing = indexedMediaDao.getVideoByUri(uri)
            if (existing != null) {
                return mapToIndexedVideo(existing)
            }
            // If we got here, something went wrong with the existing video - proceed to reindex
        }
        
        try {
            val uid = Uuid.random()

            // For a basic implementation, we'll create a video entity with default values
            // In a real implementation, we'd extract metadata from the actual video file
            val videoEntity = IndexedVideoEntity(
                uid = uid,
                uri = uri,
                timestamp = timestamp,
                indexedAt = Clock.System.now(),
                mimeType = "video/mp4",  // Assumed default
                fileSize = 0,  // Default value
                dimensions = MediaDimensions(
                    width = 0,  // Default value
                    height = 0  // Default value
                ),
                location = null,
                duration = duration
            )
            
            indexedMediaDao.insertVideo(videoEntity)
            
            return IndexedMedia.Video(
                uid = uid,
                uri = uri,
                timestamp = timestamp,
                caption = null,
                duration = duration
            )
        } catch (e: Exception) {
            Napier.e("Failed to index video", e)
            throw IllegalStateException("Failed to index video: ${e.message}", e)
        }
    }

    override suspend fun getByUid(uid: Uuid): IndexedMedia? {
        // Try as image first
        val image = indexedMediaDao.getImageById(uid).firstOrNull()
        if (image != null) {
            return mapToIndexedImage(image)
        }
        
        // Try as video
        val video = indexedMediaDao.getVideoById(uid).firstOrNull()
        if (video != null) {
            return mapToIndexedVideo(video)
        }
        
        return null
    }

    override fun getForPeriod(startTime: Instant, endTime: Instant): Flow<List<IndexedMedia>> {
        return indexedMediaDao.getImagesForPeriod(startTime, endTime).map { images ->
            val videos = indexedMediaDao.getVideosForPeriod(startTime, endTime).firstOrNull() ?: emptyList()
            
            // Combine and sort by timestamp
            val result = mutableListOf<IndexedMedia>()
            result.addAll(images.map { mapToIndexedImage(it) })
            result.addAll(videos.map { mapToIndexedVideo(it) })
            
            result.sortedBy { it.timestamp }
        }
    }

    override suspend fun isIndexed(uri: String): Boolean {
        return indexedMediaDao.isMediaIndexed(uri)
    }

    override suspend fun remove(uid: Uuid): Boolean {
        return try {
            // Try to remove as both types - only one will succeed
            val imageDeleted = indexedMediaDao.removeImage(uid)
            val videoDeleted = indexedMediaDao.removeVideo(uid)
            
            // Return true if either operation deleted something
            imageDeleted > 0 || videoDeleted > 0
        } catch (e: Exception) {
            Napier.e("Failed to remove media", e)
            false
        }
    }

    override suspend fun updateCaption(uid: Uuid, caption: String?): IndexedMedia? {
        // This will need to be implemented when we add caption support to the new entities
        // For now, return null as captions are not yet supported
        Napier.d("Caption updates not yet supported in the new entity model")
        return null
    }
    
    // Helper methods to map database entities to domain models
    
    private fun mapToIndexedImage(entity: IndexedImageEntity): IndexedMedia.Image {
        return IndexedMedia.Image(
            uid = entity.uid,
            uri = entity.uri,
            timestamp = entity.timestamp,
            caption = null  // Caption not yet supported in new entity model
        )
    }
    
    private fun mapToIndexedVideo(entity: IndexedVideoEntity): IndexedMedia.Video {
        return IndexedMedia.Video(
            uid = entity.uid,
            uri = entity.uri,
            timestamp = entity.timestamp,
            caption = null,  // Caption not yet supported in new entity model
            duration = entity.duration
        )
    }
}