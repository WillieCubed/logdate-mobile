package app.logdate.client.database.entities.rewind

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Entity representing a video in a rewind.
 * 
 * This entity stores video content included in a rewind,
 * such as videos recorded during the rewind time period.
 */
@Entity(
    tableName = "rewind_video_content",
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
data class RewindVideoContentEntity(
    @PrimaryKey
    override val id: Uuid,
    override val rewindId: Uuid,
    override val sourceId: Uuid,
    override val timestamp: Instant,
    
    /**
     * URI of the video.
     */
    val uri: String,
    
    /**
     * Optional caption for the video.
     */
    val caption: String?,
    
    /**
     * Duration of the video in a serialized Duration format.
     */
    val duration: String
) : BaseRewindContentEntity()