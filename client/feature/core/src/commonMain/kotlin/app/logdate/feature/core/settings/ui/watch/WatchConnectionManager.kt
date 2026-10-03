package app.logdate.feature.core.settings.ui.watch

import kotlinx.coroutines.flow.Flow

/**
 * Manages the connection between the phone and a paired Wear OS watch.
 *
 * Provides reactive observation of the watch connection state and
 * actions for triggering sync and managing the watch app.
 */
interface WatchConnectionManager {
    /**
     * Observes the current watch connection state.
     */
    fun observeConnectionState(): Flow<WatchConnectionState>

    /**
     * Starts the system flow to associate LogDate with a watch.
     */
    suspend fun beginAssociation()

    /**
     * Triggers an immediate sync with the connected watch.
     */
    suspend fun requestSync()

    /**
     * Opens LogDate's Play Store listing on a connected watch: one without LogDate so it can be
     * installed, or else one that has it so it can be updated.
     */
    suspend fun installAppOnWatch(): WatchRequestResult

    /**
     * Opens LogDate at Today on a connected watch that has it installed.
     */
    suspend fun openAppOnWatch(): WatchRequestResult
}
