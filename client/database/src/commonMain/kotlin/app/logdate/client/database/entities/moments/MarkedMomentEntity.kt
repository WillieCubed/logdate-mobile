package app.logdate.client.database.entities.moments

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A moment someone marked on the watch to fill in later.
 *
 * A separate record rather than a note type: older phones read notes with a closed set of types, and
 * would drop a mark and never acknowledge it. [state] only moves forward (OPEN, then DISMISSED or
 * FILLED; FILLED wins), and [resolvedNoteIds] only grows.
 *
 * @property caption Plain text here; encrypted before it leaves the device.
 * @property resolvedNoteIds The ids of the notes that filled this mark, comma-separated; empty when none.
 * @property capturedDuring The wire name of what the person was doing when they marked it, or null.
 */
@Entity(tableName = "marked_moments")
data class MarkedMomentEntity(
    @PrimaryKey
    val id: Uuid,
    val capturedAt: Instant,
    val timeZoneId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val heartRateBpm: Int? = null,
    val state: String,
    val caption: String = "",
    val resolvedNoteIds: String = "",
    val originDeviceId: String,
    val lastUpdated: Instant,
    val syncVersion: Long = 0,
    val deletedAt: Instant? = null,
    val capturedDuring: String? = null,
)

/** The stored values of [MarkedMomentEntity.state]. */
object MarkedMomentState {
    const val OPEN = "OPEN"
    const val DISMISSED = "DISMISSED"
    const val FILLED = "FILLED"
}
