package app.logdate.client.sync

import androidx.room.RoomDatabase

/**
 * Room-based implementation of SyncTransactionManager for iOS.
 * iOS does not expose Room's JVM transaction APIs, so we run the block directly.
 */
class RoomSyncTransactionManager(
    private val database: RoomDatabase
) : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}
