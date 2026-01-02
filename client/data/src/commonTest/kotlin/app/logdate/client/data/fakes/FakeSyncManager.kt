package app.logdate.client.data.fakes

import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus

/**
 * Fake implementation of [SyncManager] for testing.
 */
class FakeSyncManager : SyncManager {
    private var syncRequested = false
    private var immediateSync = false
    private var syncCount = 0
    var uploadPendingChangesCalls = 0
    var downloadRemoteChangesCalls = 0
    var syncContentCalls = 0
    var syncJournalsCalls = 0
    var syncAssociationsCalls = 0
    var fullSyncCalls = 0
    var getSyncStatusCalls = 0
    var syncResult: SyncResult = SyncResult(success = true)
    var syncStatus: SyncStatus = SyncStatus(
        isEnabled = true,
        lastSyncTime = null,
        pendingUploads = 0,
        isSyncing = false,
        hasErrors = false,
        lastError = null
    )
    
    override fun sync(startNow: Boolean) {
        syncRequested = true
        immediateSync = startNow
        syncCount++
    }

    override suspend fun uploadPendingChanges(): SyncResult {
        uploadPendingChangesCalls++
        return syncResult
    }

    override suspend fun downloadRemoteChanges(): SyncResult {
        downloadRemoteChangesCalls++
        return syncResult
    }

    override suspend fun syncContent(): SyncResult {
        syncContentCalls++
        return syncResult
    }

    override suspend fun syncJournals(): SyncResult {
        syncJournalsCalls++
        return syncResult
    }

    override suspend fun syncAssociations(): SyncResult {
        syncAssociationsCalls++
        return syncResult
    }

    override suspend fun fullSync(): SyncResult {
        fullSyncCalls++
        return syncResult
    }

    override suspend fun getSyncStatus(): SyncStatus {
        getSyncStatusCalls++
        return syncStatus
    }
    
    /**
     * Returns whether sync was requested.
     */
    fun wasSyncRequested(): Boolean {
        return syncRequested
    }
    
    /**
     * Returns whether immediate sync was requested.
     */
    fun wasImmediateSyncRequested(): Boolean {
        return immediateSync
    }
    
    /**
     * Returns the number of times sync was called.
     */
    fun getSyncCount(): Int {
        return syncCount
    }
    
    /**
     * Resets the sync state for testing.
     */
    fun reset() {
        syncRequested = false
        immediateSync = false
        syncCount = 0
        uploadPendingChangesCalls = 0
        downloadRemoteChangesCalls = 0
        syncContentCalls = 0
        syncJournalsCalls = 0
        syncAssociationsCalls = 0
        fullSyncCalls = 0
        getSyncStatusCalls = 0
        syncResult = SyncResult(success = true)
        syncStatus = SyncStatus(
            isEnabled = true,
            lastSyncTime = null,
            pendingUploads = 0,
            isSyncing = false,
            hasErrors = false,
            lastError = null
        )
    }
}
