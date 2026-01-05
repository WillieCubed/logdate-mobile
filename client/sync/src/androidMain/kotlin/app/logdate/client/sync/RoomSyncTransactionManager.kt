package app.logdate.client.sync

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Room-based implementation of SyncTransactionManager.
 * Uses Room's built-in transaction support for SQLite databases.
 */
class RoomSyncTransactionManager(private val database: RoomDatabase) : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        return database.withTransaction(block)
    }
}
