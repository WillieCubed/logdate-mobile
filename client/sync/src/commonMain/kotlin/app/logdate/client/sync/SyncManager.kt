package app.logdate.client.sync

import kotlinx.datetime.Instant

/**
 * A generic interface for syncing the client with the user's remote data.
 *
 * Implementations of this interface should be platform-specific.
 */
interface SyncManager {
    /**
     * Begin syncing the client with the user's remote data.
     *
     * By default, this method does not start syncing immediately and instead syncs data
     * asynchronously when resources are available.
     *
     * @param startNow Whether to start syncing immediately. Note that this parameter may be ignored
     *                 by some implementations. False by default.
     */
    fun sync(startNow: Boolean = false)
    
    /**
     * Uploads all pending local changes to the cloud.
     * This includes journals, notes, associations, and media.
     */
    suspend fun uploadPendingChanges(): SyncResult
    
    /**
     * Downloads and applies all remote changes since the last sync.
     * This includes journals, notes, associations, and media.
     */
    suspend fun downloadRemoteChanges(): SyncResult
    
    /**
     * Syncs content (notes) specifically.
     * Uploads local changes and downloads remote changes.
     */
    suspend fun syncContent(): SyncResult
    
    /**
     * Syncs journal metadata specifically.
     * Uploads local changes and downloads remote changes.
     */
    suspend fun syncJournals(): SyncResult
    
    /**
     * Syncs journal-content associations specifically.
     * Uploads local changes and downloads remote changes.
     */
    suspend fun syncAssociations(): SyncResult
    
    /**
     * Performs a full bidirectional sync of all data.
     * This is the most comprehensive sync operation.
     */
    suspend fun fullSync(): SyncResult
    
    /**
     * Gets the current sync status including last sync time and pending changes count.
     */
    suspend fun getSyncStatus(): SyncStatus
}

/**
 * Result of a sync operation indicating success/failure and what was synced.
 */
data class SyncResult(
    val success: Boolean,
    val uploadedItems: Int = 0,
    val downloadedItems: Int = 0,
    val conflictsResolved: Int = 0,
    val errors: List<SyncError> = emptyList(),
    val lastSyncTime: Instant? = null
)

/**
 * Current sync status of the client.
 */
data class SyncStatus(
    val isEnabled: Boolean,
    val lastSyncTime: Instant?,
    val pendingUploads: Int,
    val isSyncing: Boolean,
    val hasErrors: Boolean,
    val lastError: SyncError? = null
)

/**
 * Represents a sync error that occurred during a sync operation.
 */
data class SyncError(
    val type: SyncErrorType,
    val message: String,
    val cause: Throwable? = null,
    val retryable: Boolean = true
)

/**
 * Types of sync errors that can occur.
 */
enum class SyncErrorType {
    NETWORK_ERROR,
    AUTHENTICATION_ERROR,
    SERVER_ERROR,
    CONFLICT_ERROR,
    STORAGE_ERROR,
    UNKNOWN_ERROR
}