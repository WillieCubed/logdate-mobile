package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "image_notes"
)
data class ImageNoteEntity(
    val contentUri: String,
    @PrimaryKey(autoGenerate = true)
    override val uid: Int,
    override val lastUpdated: Instant,
    override val created: Instant,
) : GenericNoteData()