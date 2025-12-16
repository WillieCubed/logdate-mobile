package app.logdate.client.database.entities.rewind

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Entity representing an image in a rewind.
 * 
 * This entity stores image content included in a rewind,
 * such as photos taken during the rewind time period.
 */
@Entity(
    tableName = "rewind_image_content",
    foreignKeys = [
        ForeignKey(
            entity = RewindEntity::class,
            parentColumns = [RewindConstants.COLUMN_UID],
            childColumns = [RewindConstants.COLUMN_REWIND_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(RewindConstants.COLUMN_REWIND_ID),
        Index(RewindConstants.COLUMN_SOURCE_ID)
    ]
)
data class RewindImageContentEntity(
    @PrimaryKey
    override val id: Uuid,
    override val rewindId: Uuid,
    override val sourceId: Uuid,
    override val timestamp: Instant,
    
    /**
     * URI of the image.
     */
    val uri: String,
    
    /**
     * Optional caption for the image.
     */
    val caption: String?
) : BaseRewindContentEntity()