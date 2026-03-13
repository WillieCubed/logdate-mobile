package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

/**
 * Stores a user-written caption for a visual media note (image or video).
 *
 * Audio notes are excluded — they surface user intent through transcription instead.
 * One row per note; [noteId] is the UID of the corresponding image or video note.
 */
@Entity(tableName = "media_captions")
data class MediaCaptionEntity(
    @PrimaryKey
    val noteId: Uuid,
    val caption: String,
)
