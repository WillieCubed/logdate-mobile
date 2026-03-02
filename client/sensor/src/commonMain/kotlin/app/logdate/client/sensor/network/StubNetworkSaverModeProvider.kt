package app.logdate.client.sensor.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A stub implementation of [NetworkSaverModeProvider] for testing or platforms
 * where network data saver mode information is not available.
 */
class StubNetworkSaverModeProvider : NetworkSaverModeProvider {
    private val networkSaverStateFlow =
        MutableStateFlow(
            NetworkSaverState(
                isDataSaverEnabled = false,
                connectionType = NetworkConnectionType.WIFI,
            ),
        )

    override val dataSaverModeState: Flow<NetworkSaverState> = networkSaverStateFlow

    override suspend fun getCurrentDataSaverState(): NetworkSaverState = networkSaverStateFlow.value

    override suspend fun isDataSaverModeActive(): Boolean = networkSaverStateFlow.value.isDataSaverEnabled

    /**
     * Set a custom network saver state for testing purposes.
     */
    fun setNetworkSaverState(networkSaverState: NetworkSaverState) {
        networkSaverStateFlow.value = networkSaverState
    }

    override fun cleanup() {
        // No-op for stub implementation
    }
}
