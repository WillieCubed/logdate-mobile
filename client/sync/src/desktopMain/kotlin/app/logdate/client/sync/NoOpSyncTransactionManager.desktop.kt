package app.logdate.client.sync

/**
 * No-op implementation of SyncTransactionManager for Desktop.
 * Executes the provided block without transaction semantics.
 */
class NoOpSyncTransactionManager : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}
