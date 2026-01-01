package app.logdate.client.database.entities.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the last sync timestamp for each entity type.
 * Used to track where delta sync should resume from.
 */
@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey
    val entityType: String,
    val lastSyncTimestamp: Long
)
