package app.logdate.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "text_notes",
)
data class TextNoteEntity(
    val content: String,
    @PrimaryKey(autoGenerate = true)
    override val uid: Int,
    override val lastUpdated: Instant,
    override val created: Instant,
) : GenericNoteData()