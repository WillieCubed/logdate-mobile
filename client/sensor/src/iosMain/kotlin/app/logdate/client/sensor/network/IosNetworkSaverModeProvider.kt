package app.logdate.client.sensor.network

import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.networking.saver.NetworkSaverState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSURLSessionConfiguration

class IosNetworkSaverModeProvider : NetworkSaverModeProvider {
    // URLSessionConfiguration gives us access to constrained network access settings
    private val sessionConfig = NSURLSessionConfiguration.defaultSessionConfiguration()
    private val networkSaverStateFlow = MutableStateFlow(getCurrentNetworkSaverStateInternal())

    override val dataSaverModeState: Flow<NetworkSaverState> = networkSaverStateFlow.asStateFlow()

    override suspend fun getCurrentDataSaverState(): NetworkSaverState = getCurrentNetworkSaverStateInternal()

    override suspend fun isDataSaverModeActive(): Boolean {
        // iOS has Low Data Mode which can be detected through URLSessionConfiguration
        // !allowsConstrainedNetworkAccess means Low Data Mode is active
        return !sessionConfig.allowsConstrainedNetworkAccess
    }

    override fun cleanup() {
        // No-op: no background monitoring on iOS yet.
    }

    private fun getCurrentNetworkSaverStateInternal(): NetworkSaverState {
        val isDataSaverEnabled = !sessionConfig.allowsConstrainedNetworkAccess

        return NetworkSaverState(
            isDataSaverEnabled = isDataSaverEnabled,
            connectionType = NetworkConnectionType.OTHER,
        )
    }
}
