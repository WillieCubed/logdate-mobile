package app.logdate.client.database.entities.media

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Entity for indexed video media.
 *
 * This entity represents video files that have been indexed by the application
 * and contains video-specific properties.
 */
@Entity(
    tableName = "indexed_media_videos",
    indices = [
        Index(value = ["mediaId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = IndexedMediaEntity::class,
            parentColumns = ["uid"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class IndexedMediaVideoEntity(
    @PrimaryKey
    val id: Uuid,
    
    /**
     * Reference to the base media entity.
     */
    val mediaId: Uuid,
    
    /**
     * Width of the video in pixels.
     */
    val width: Int,
    
    /**
     * Height of the video in pixels.
     */
    val height: Int,
    
    /**
     * MIME type of the video (e.g., "video/mp4").
     */
    val mimeType: String,
    
    /**
     * Size of the video file in bytes.
     */
    val fileSize: Long,
    
    /**
     * Duration of the video in milliseconds.
     */
    val durationMs: Long,
    
    /**
     * Whether the video has location data.
     */
    val hasLocationData: Boolean = false,
    
    /**
     * Optional location data - latitude.
     */
    val latitude: Double? = null,
    
    /**
     * Optional location data - longitude.
     */
    val longitude: Double? = null,
    
    /**
     * Optional thumbnail frame image URI.
     */
    val thumbnailFrameUri: String? = null,
)