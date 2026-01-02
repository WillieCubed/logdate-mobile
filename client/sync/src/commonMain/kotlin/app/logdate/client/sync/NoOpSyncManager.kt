package app.logdate.client.sync

/**
 * No-op sync manager for environments where sync is unavailable.
 */
object NoOpSyncManager : SyncManager {
    private val noOpResult = SyncResult(success = true)
    private val noOpStatus = SyncStatus(
        isEnabled = false,
        lastSyncTime = null,
        pendingUploads = 0,
        isSyncing = false,
        hasErrors = false,
        lastError = null
    )

    override fun sync(startNow: Boolean) {
        // No-op.
    }

    override suspend fun uploadPendingChanges(): SyncResult = noOpResult

    override suspend fun downloadRemoteChanges(): SyncResult = noOpResult

    override suspend fun syncContent(): SyncResult = noOpResult

    override suspend fun syncJournals(): SyncResult = noOpResult

    override suspend fun syncAssociations(): SyncResult = noOpResult

    override suspend fun fullSync(): SyncResult = noOpResult

    override suspend fun getSyncStatus(): SyncStatus = noOpStatus
}
