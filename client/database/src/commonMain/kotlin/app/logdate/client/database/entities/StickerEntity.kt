package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Room entity for a user-extracted sticker.
 *
 * Stickers are created by running on-device segmentation on a photo to extract
 * a subject (person, pet, object). The extracted image is stored as a PNG with
 * transparency at [imageUri].
 */
@Entity(
    tableName = "stickers",
    indices = [
        Index("created_at"),
        Index("source_moment_ref"),
    ],
)
data class StickerEntity(
    @PrimaryKey
    val id: Uuid,
    @ColumnInfo(name = "source_photo_uri")
    val sourcePhotoUri: String,
    @ColumnInfo(name = "source_moment_ref")
    val sourceMomentRef: Uuid? = null,
    @ColumnInfo(name = "image_uri")
    val imageUri: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    val label: String? = null,
)
