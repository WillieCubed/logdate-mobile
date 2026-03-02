package app.logdate.client.sync

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-based implementation of SyncTransactionManager for JVM/desktop.
 * Uses Room's transaction APIs to ensure atomic sync applies.
 */
class RoomSyncTransactionManager(
    private val database: RoomDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T =
        withContext(ioDispatcher) {
            database.useWriterConnection { transactor ->
                transactor.immediateTransaction { block() }
            }
        }
}
