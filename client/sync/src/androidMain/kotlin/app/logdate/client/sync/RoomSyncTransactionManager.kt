package app.logdate.client.sync

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import app.logdate.client.media.storage.MediaClaimFence

/**
 * Room-based implementation of SyncTransactionManager.
 * Uses Room's built-in transaction support for SQLite databases.
 */
class RoomSyncTransactionManager(
    private val database: RoomDatabase,
    private val mediaClaimFence: MediaClaimFence = MediaClaimFence.Disabled,
) : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T =
        mediaClaimFence.withClaim {
            withSyncTransactionCompletion(this, { operation -> database.withTransaction(operation) }, block)
        }
}
