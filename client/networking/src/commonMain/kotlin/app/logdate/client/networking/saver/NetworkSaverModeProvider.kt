package app.logdate.client.networking.saver

import kotlinx.coroutines.flow.Flow

/**
 * Provides information about the device's network data saver mode.
 *
 * Platform implementations live in the sensor module; this interface is owned
 * by networking so the networking module can consume it without depending on sensors.
 */
interface NetworkSaverModeProvider {
    val dataSaverModeState: Flow<NetworkSaverState>

    suspend fun getCurrentDataSaverState(): NetworkSaverState

    suspend fun isDataSaverModeActive(): Boolean

    fun cleanup()
}
