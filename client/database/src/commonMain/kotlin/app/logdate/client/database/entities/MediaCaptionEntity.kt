package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

/**
 * Stores a user-written caption for an image, audio, or video note.
 *
 * One row per note; [noteId] is the UID of the corresponding media note.
 */
@Entity(tableName = "media_captions")
data class MediaCaptionEntity(
    @PrimaryKey
    val noteId: Uuid,
    val caption: String,
)
