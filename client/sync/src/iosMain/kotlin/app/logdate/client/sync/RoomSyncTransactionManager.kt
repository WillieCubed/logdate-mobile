package app.logdate.client.sync

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Room-based implementation of SyncTransactionManager for iOS.
 * Uses Room's KMP writer connection so association writes and sync metadata commit together.
 */
class RoomSyncTransactionManager(
    private val database: RoomDatabase,
) : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { block() }
        }
}
