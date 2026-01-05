package app.logdate.client.sync

/**
 * No-op implementation of SyncTransactionManager for iOS.
 * Simply executes the block without transaction semantics.
 */
class NoOpSyncTransactionManager : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        return block()
    }
}
