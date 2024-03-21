package app.logdate.core.database.model

import androidx.room.Entity
import kotlinx.datetime.Instant

@Entity(
    tableName = "text_notes"
)
data class TextNoteEntity(
    val content: String,
    override val uid: Int,
    override val lastUpdated: Instant,
    override val created: Instant,
) : GenericNote()