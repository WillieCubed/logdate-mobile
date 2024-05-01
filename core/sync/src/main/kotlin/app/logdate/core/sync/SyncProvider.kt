package app.logdate.core.sync

import kotlinx.coroutines.flow.Flow

/**
 * A service that allows for syncing data between the local database and a remote server.
 */
interface SyncProvider {

    /**
     * Whether the provider is currently syncing data.
     */
    val isSyncing: Flow<Boolean>

    /**
     * Triggers a refresh of the data from the remote server.
     */
    fun sync()
}