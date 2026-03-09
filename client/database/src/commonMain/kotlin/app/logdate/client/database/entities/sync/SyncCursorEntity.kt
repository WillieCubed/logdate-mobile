package app.logdate.client.database.entities.sync

import androidx.room.Entity

/**
 * Stores the last sync timestamp for each entity type.
 * Used to track where delta sync should resume from.
 */
@Entity(
    tableName = "sync_cursors",
    primaryKeys = ["serverOrigin", "entityType"],
)
data class SyncCursorEntity(
    val serverOrigin: String,
    val entityType: String,
    val lastSyncTimestamp: Long,
)
