package app.logdate.client.database.entities.media

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

/**
 * Entity for indexed image media.
 *
 * This entity represents image files that have been indexed by the application
 * and contains image-specific properties.
 */
@Entity(
    tableName = "indexed_media_images",
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
data class IndexedMediaImageEntity(
    @PrimaryKey
    val id: Uuid,
    
    /**
     * Reference to the base media entity.
     */
    val mediaId: Uuid,
    
    /**
     * Width of the image in pixels.
     */
    val width: Int,
    
    /**
     * Height of the image in pixels.
     */
    val height: Int,
    
    /**
     * MIME type of the image (e.g., "image/jpeg", "image/png").
     */
    val mimeType: String,
    
    /**
     * Size of the image file in bytes.
     */
    val fileSize: Long,
    
    /**
     * Whether the image has EXIF location data.
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
)