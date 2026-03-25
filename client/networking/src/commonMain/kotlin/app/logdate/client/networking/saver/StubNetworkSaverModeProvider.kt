package app.logdate.client.networking.saver

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * No-op [NetworkSaverModeProvider] for platforms or tests that don't support data saver detection.
 */
class StubNetworkSaverModeProvider : NetworkSaverModeProvider {
    override val dataSaverModeState: Flow<NetworkSaverState> =
        MutableStateFlow(
            NetworkSaverState(
                isDataSaverEnabled = false,
                connectionType = NetworkConnectionType.WIFI,
            ),
        )

    override suspend fun getCurrentDataSaverState(): NetworkSaverState =
        NetworkSaverState(isDataSaverEnabled = false, connectionType = NetworkConnectionType.WIFI)

    override suspend fun isDataSaverModeActive(): Boolean = false

    override fun cleanup() {}
}
