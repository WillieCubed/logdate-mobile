package app.logdate.core.database.model.journals

import androidx.room.ColumnInfo
import androidx.room.Relation
import app.logdate.core.database.model.JournalEntity

/**
 * A wrapper entity that relates content to a journal.
 */
data class JournalContentEntityLink(
    /**
     * The
     */
    @Relation(
        entityColumn = "journal_container_id",
        parentColumn = "id",
        entity = JournalEntity::class,
    )
    val journalId: String,
    /**
     * The UID of the content in this journal.
     */
    @ColumnInfo("content_id")
    val contentId: String,
)