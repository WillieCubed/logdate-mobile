package app.logdate.client.database.entities.media

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Base entity for all indexed media types.
 *
 * This entity represents external media files (images, videos) that have been
 * indexed by the application for inclusion in journals, rewinds, etc.
 */
@Entity(
    tableName = "indexed_media",
    indices = [
        Index(value = ["uri"], unique = true)
    ]
)
data class IndexedMediaEntity(
    @PrimaryKey
    val uid: Uuid,
    
    /**
     * Original URI of the media on the device.
     */
    val uri: String,
    
    /**
     * Type of media (image, video).
     */
    val mediaType: MediaType,
    
    /**
     * The timestamp when the media was created.
     */
    val timestamp: Instant,
    
    /**
     * Optional user-provided caption for the media.
     */
    val caption: String? = null,
    
    /**
     * The timestamp when the media was indexed.
     */
    val indexedAt: Instant,
    
    /**
     * Whether this media has been processed for entities like people, places, etc.
     */
    val processed: Boolean = false,
    
    /**
     * Optional thumbnail URI for quick loading.
     */
    val thumbnailUri: String? = null,
) {
    /**
     * Type of indexed media.
     */
    enum class MediaType {
        IMAGE,
        VIDEO,
    }
}