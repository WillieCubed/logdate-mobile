package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Many-to-many junction between events and journal notes.
 *
 * Mirrors the journal↔note linking pattern: an event can collect any number of notes (text,
 * image, video, audio) that belong to it, and a note can be referenced by multiple events.
 */
@Entity(
    tableName = "event_note_links",
    primaryKeys = ["event_id", "note_id"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["note_id"]),
    ],
)
data class EventNoteLinkEntity(
    @ColumnInfo(name = "event_id")
    val eventId: Uuid,
    @ColumnInfo(name = "note_id")
    val noteId: Uuid,
    @ColumnInfo(name = "sync_version")
    val syncVersion: Long = 0,
    @ColumnInfo(name = "last_synced")
    val lastSynced: Instant? = null,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Instant? = null,
)
