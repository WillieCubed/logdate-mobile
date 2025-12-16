package app.logdate.client.sync

/**
 * A platform-specific implementation of [SyncManager] for iOS.
 * 
 * This delegates to DefaultSyncManager for core functionality and adds
 * any iOS-specific sync behaviors if needed.
 */
class IosSyncManager(
    private val defaultSyncManager: DefaultSyncManager
) : SyncManager {

    override fun sync(startNow: Boolean) {
        defaultSyncManager.sync(startNow)
    }
    
    override suspend fun uploadPendingChanges(): SyncResult {
        return defaultSyncManager.uploadPendingChanges()
    }
    
    override suspend fun downloadRemoteChanges(): SyncResult {
        return defaultSyncManager.downloadRemoteChanges()
    }
    
    override suspend fun syncContent(): SyncResult {
        return defaultSyncManager.syncContent()
    }
    
    override suspend fun syncJournals(): SyncResult {
        return defaultSyncManager.syncJournals()
    }
    
    override suspend fun syncAssociations(): SyncResult {
        return defaultSyncManager.syncAssociations()
    }
    
    override suspend fun fullSync(): SyncResult {
        return defaultSyncManager.fullSync()
    }
    
    override suspend fun getSyncStatus(): SyncStatus {
        return defaultSyncManager.getSyncStatus()
    }
}