package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(
    tableName = "location_activity",
    indices = [Index(value = ["timestamp"]), Index(value = ["user_id", "device_id", "recorded_at", "id"])],
)
data class LocationActivityEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val timestamp: Instant,
    @ColumnInfo(name = "recorded_at") val recordedAt: Instant,
    @ColumnInfo(name = "activity_type") val activityType: String,
    @ColumnInfo(name = "transition_type") val transitionType: String,
    @ColumnInfo(name = "time_zone_id") val timeZoneId: String?,
)
