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
     * Triggers an immediate sync with the connected watch.
     */
    suspend fun requestSync()

    /**
     * Launches the Play Store on the watch to install LogDate.
     */
    suspend fun installAppOnWatch()

    /**
     * Opens LogDate on the connected watch.
     */
    suspend fun openAppOnWatch()
}
