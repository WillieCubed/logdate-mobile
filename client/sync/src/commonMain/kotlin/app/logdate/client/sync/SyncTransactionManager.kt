package app.logdate.client.sync

/**
 * Manages database transactions for sync operations.
 * Ensures atomic writes of batches to prevent partial applies.
 */
interface SyncTransactionManager {
    /**
     * Executes the provided lambda within a database transaction.
     * If the lambda throws an exception, all writes are rolled back.
     * Otherwise, all writes are committed atomically.
     */
    suspend fun <T> withTransaction(block: suspend () -> T): T
}
