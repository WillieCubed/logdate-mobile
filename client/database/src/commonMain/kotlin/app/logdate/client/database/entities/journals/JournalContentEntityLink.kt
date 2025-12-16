package app.logdate.client.database.entities.journals

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import app.logdate.client.database.entities.JournalEntity
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * An entity that relates content to a journal.
 * This creates a many-to-many relationship between journals and content.
 */
@Entity(
    tableName = "journal_content_links",
    primaryKeys = ["journal_id", "content_id"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntity::class,
            parentColumns = ["id"],
            childColumns = ["journal_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["journal_id"]),
        Index(value = ["content_id"])
    ]
)
data class JournalContentEntityLink(
    /**
     * The ID of the journal this content belongs to.
     */
    @ColumnInfo(name = "journal_id")
    val journalId: Uuid,
    
    /**
     * The UID of the content in this journal.
     */
    @ColumnInfo(name = "content_id")
    val contentId: Uuid,
    
    /**
     * Sync metadata for tracking changes to this association.
     */
    val syncVersion: Long = 0,
    val lastSynced: Instant? = null,
    val deletedAt: Instant? = null,
)