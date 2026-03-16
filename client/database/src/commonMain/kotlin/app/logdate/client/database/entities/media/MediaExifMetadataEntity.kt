package app.logdate.client.database.entities.media

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * EXIF/camera metadata extracted from a media file.
 *
 * Stored separately from the main media entity to keep primary queries fast.
 * EXIF extraction is a background operation that populates this table lazily.
 */
@Entity(tableName = "media_exif_metadata")
data class MediaExifMetadataEntity(
    @PrimaryKey
    val mediaUid: Uuid,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val aperture: Double? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val shutterSpeed: String? = null,
    val whiteBalance: String? = null,
    val flashFired: Boolean? = null,
    val orientation: Int? = null,
    val extractedAt: Instant,
)
