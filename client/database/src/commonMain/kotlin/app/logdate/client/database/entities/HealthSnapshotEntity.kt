package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "health_snapshots",
    indices = [
        Index("note_id"),
        Index("timestamp"),
    ],
)
data class HealthSnapshotEntity(
    @PrimaryKey
    val id: Uuid = Uuid.random(),
    @ColumnInfo(name = "note_id")
    val noteId: Uuid? = null,
    @ColumnInfo(name = "heart_rate_bpm")
    val heartRateBpm: Int? = null,
    @ColumnInfo(name = "heart_rate_variability_ms")
    val heartRateVariabilityMs: Float? = null,
    @ColumnInfo(name = "step_count")
    val stepCount: Int? = null,
    @ColumnInfo(name = "stress_level")
    val stressLevel: Float? = null,
    @ColumnInfo(name = "cumulative_calories")
    val cumulativeCalories: Float? = null,
    val timestamp: Instant,
    val source: String,
)
