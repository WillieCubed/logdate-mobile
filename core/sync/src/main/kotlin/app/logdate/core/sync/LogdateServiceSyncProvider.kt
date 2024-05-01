package app.logdate.core.sync

import kotlinx.coroutines.flow.Flow

/**
 * A service that allows for syncing data between the local database and a remote server.
 *
 * A backup and sync provider is responsible for syncing data between the local database and a remote server.
 */
interface LogdateServiceSyncProvider {

    /**
     * Whether this provider may be used to sync data.
     */
    val enabled: Boolean

    /**
     * Whether the provider is currently syncing data.
     */
    val isSyncing: Flow<Boolean>

    /**
     * Triggers a refresh of the data from the remote server.
     *
     * @param overwriteLocal Whether to overwrite local data with the remote data, false by default.
     */
    fun sync(overwriteLocal: Boolean = false)
}