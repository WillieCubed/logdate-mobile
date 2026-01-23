package app.logdate.client.sync

import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Room-based implementation of SyncTransactionManager for JVM/desktop.
 * Uses Room's transaction APIs to ensure atomic sync applies.
 */
class RoomSyncTransactionManager(
    private val database: RoomDatabase
) : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        var result: Result<T>? = null
        database.runInTransaction {
            result = runBlocking { runCatching { block() } }
        }
        return@withContext result?.getOrThrow()
            ?: error("Sync transaction failed to capture result")
    }
}
