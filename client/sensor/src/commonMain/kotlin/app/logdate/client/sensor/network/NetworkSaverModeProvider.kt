package app.logdate.client.sensor.network

import kotlinx.coroutines.flow.Flow

/**
 * Provides information about the device's network data saver mode.
 * 
 * This interface allows monitoring of the device's data saver mode status,
 * which can be used to adjust app behavior to reduce data usage when
 * the user has enabled data saving features on their device.
 */
interface NetworkSaverModeProvider {
    /**
     * A flow that emits the current network data saver mode state.
     * Will emit new values when the data saver mode status changes.
     */
    val dataSaverModeState: Flow<NetworkSaverState>
    
    /**
     * Returns the current network data saver state without subscribing to updates.
     */
    suspend fun getCurrentDataSaverState(): NetworkSaverState
    
    /**
     * Convenience method to check if data saver mode is currently active.
     */
    suspend fun isDataSaverModeActive(): Boolean
    
    /**
     * Releases resources and observers associated with network state monitoring.
     */
    fun cleanup()
}