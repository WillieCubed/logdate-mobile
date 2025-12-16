package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "video_notes"
)
data class VideoNoteEntity(
    val contentUri: String,
    /**
     * Duration of the video in milliseconds.
     */
    val durationMs: Long? = null,
    @PrimaryKey
    override val uid: Uuid = Uuid.random(),
    override val lastUpdated: Instant,
    override val created: Instant,
    override val syncVersion: Long = 0,
    override val lastSynced: Instant? = null,
    override val deletedAt: Instant? = null,
) : GenericNoteData()