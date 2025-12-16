package app.logdate.client.data.fakes

import app.logdate.client.sync.SyncManager

/**
 * Fake implementation of [SyncManager] for testing.
 */
class FakeSyncManager : SyncManager {
    private var syncRequested = false
    private var immediateSync = false
    private var syncCount = 0
    
    override fun sync(startNow: Boolean) {
        syncRequested = true
        immediateSync = startNow
        syncCount++
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
    }
}