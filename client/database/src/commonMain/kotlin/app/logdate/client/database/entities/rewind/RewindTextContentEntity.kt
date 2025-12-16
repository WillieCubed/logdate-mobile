package app.logdate.client.database.entities.rewind

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Entity representing a text note in a rewind.
 * 
 * This entity stores text content such as journal entries or notes
 * that are included in a rewind.
 */
@Entity(
    tableName = "rewind_text_content",
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
data class RewindTextContentEntity(
    @PrimaryKey
    override val id: Uuid,
    override val rewindId: Uuid,
    override val sourceId: Uuid,
    override val timestamp: Instant,
    
    /**
     * The text content of the note.
     */
    val content: String
) : BaseRewindContentEntity()