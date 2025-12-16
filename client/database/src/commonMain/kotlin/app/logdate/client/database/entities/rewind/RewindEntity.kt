package app.logdate.client.database.entities.rewind

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Database entity representing a Rewind.
 * 
 * This entity stores the metadata for a rewind. The actual content
 * of the rewind is stored in separate content entity tables for
 * different content types.
 */
@Entity(
    tableName = "rewinds",
    indices = [
        Index(
            value = [
                RewindConstants.COLUMN_START_DATE, 
                RewindConstants.COLUMN_END_DATE
            ], 
            unique = true
        )
    ]
)
data class RewindEntity(
    /**
     * Unique identifier for this rewind.
     */
    @PrimaryKey
    @ColumnInfo(name = RewindConstants.COLUMN_UID)
    val uid: Uuid,

    /**
     * Start of the time period for this rewind.
     */
    @ColumnInfo(name = RewindConstants.COLUMN_START_DATE)
    val startDate: Instant,

    /**
     * End of the time period for this rewind.
     */
    @ColumnInfo(name = RewindConstants.COLUMN_END_DATE)
    val endDate: Instant,

    /**
     * When this rewind was generated.
     */
    val generationDate: Instant,

    /**
     * A short label for the rewind (e.g., "2024#42").
     */
    val label: String,

    /**
     * A user-friendly title for the rewind.
     */
    val title: String
)