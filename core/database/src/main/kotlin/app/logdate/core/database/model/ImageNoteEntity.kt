package app.logdate.core.database.model

import androidx.room.Entity
import kotlinx.datetime.Instant

@Entity(
    tableName = "image_notes"
)
data class ImageNoteEntity(
    val contentUri: String,
    override val uid: Int,
    override val lastUpdated: Instant,
    override val created: Instant,
) : GenericNote()