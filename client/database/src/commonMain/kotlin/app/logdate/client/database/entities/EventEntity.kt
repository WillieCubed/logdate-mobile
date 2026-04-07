package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A time-bound event that media and notes can attach to.
 *
 * Events represent things that happen — a piano recital, a birthday party, a trip. Unlike journals
 * (long-lived containers), events are discrete and calendar-visible. Events are not user-created;
 * they are auto-generated or grounded from external calendar sources. Users can edit metadata.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["place_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("start_time"),
        Index("place_id"),
        Index("external_calendar_id"),
    ],
)
data class EventEntity(
    @PrimaryKey
    val id: Uuid,
    val title: String,
    val description: String? = null,
    @ColumnInfo(name = "start_time")
    val startTime: Instant,
    @ColumnInfo(name = "end_time")
    val endTime: Instant? = null,
    @ColumnInfo(name = "place_id")
    val placeId: Uuid? = null,
    @ColumnInfo(name = "cover_image_uri")
    val coverImageUri: String? = null,
    /**
     * The external calendar event identifier (e.g., a Google Calendar event ID) that this event
     * is grounded in, if any. Used to reconcile updates from the source calendar.
     */
    @ColumnInfo(name = "external_calendar_id")
    val externalCalendarId: String? = null,
    /**
     * The source system for [externalCalendarId]. Stored as a string for forward compatibility
     * with sources beyond Google Calendar and Apple Calendar.
     */
    @ColumnInfo(name = "external_calendar_source")
    val externalCalendarSource: String? = null,
    val created: Instant,
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Instant,
    @ColumnInfo(name = "sync_version")
    val syncVersion: Long = 0,
    @ColumnInfo(name = "last_synced")
    val lastSynced: Instant? = null,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Instant? = null,
)
