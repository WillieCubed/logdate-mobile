package app.logdate.client.database.entities.media

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Entity for indexed image media.
 *
 * This entity represents image files that have been indexed by the application.
 * It extends the base media entity class with image-specific properties.
 */
@Entity(
    tableName = "indexed_media_images",
    indices = [
        Index(value = ["uri"], unique = true)
    ]
)
data class IndexedImageEntity(
    @PrimaryKey
    override val uid: Uuid,
    override val uri: String,
    override val timestamp: Instant,
    override val indexedAt: Instant,
    override val mimeType: String,
    override val fileSize: Long,
    @Embedded(prefix = "dimensions_")
    override val dimensions: MediaDimensions,
    @Embedded(prefix = "location_")
    override val location: LocationData?
) : BaseIndexedMediaEntity()

/**
 * Extension property to check if this image has location data.
 */
val IndexedImageEntity.hasLocationData: Boolean
    get() = location != null