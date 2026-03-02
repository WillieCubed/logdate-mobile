package app.logdate.client.sync

/**
 * A platform-specific implementation of [SyncManager] for Desktop.
 *
 * This delegates to DefaultSyncManager for core functionality and adds
 * any desktop-specific sync behaviors if needed.
 */
class DesktopSyncManager(
    private val defaultSyncManager: DefaultSyncManager,
) : SyncManager {
    override fun sync(startNow: Boolean) {
        defaultSyncManager.sync(startNow)
    }

    override suspend fun uploadPendingChanges(): SyncResult = defaultSyncManager.uploadPendingChanges()

    override suspend fun downloadRemoteChanges(): SyncResult = defaultSyncManager.downloadRemoteChanges()

    override suspend fun syncContent(): SyncResult = defaultSyncManager.syncContent()

    override suspend fun syncJournals(): SyncResult = defaultSyncManager.syncJournals()

    override suspend fun syncAssociations(): SyncResult = defaultSyncManager.syncAssociations()

    override suspend fun fullSync(): SyncResult = defaultSyncManager.fullSync()

    override suspend fun getSyncStatus(): SyncStatus = defaultSyncManager.getSyncStatus()
}
