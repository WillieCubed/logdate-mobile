package app.logdate.client.networking

import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.networking.saver.NetworkSaverState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Default implementation of [DataUsagePolicy] that derives [DataUsageMode]
 * from the platform's [NetworkSaverModeProvider].
 *
 * The mapping is:
 * - No connection → [DataUsageMode.Restricted]
 * - Data Saver enabled → [DataUsageMode.Restricted]
 * - Cellular, Data Saver off → [DataUsageMode.Conservative]
 * - WiFi/Ethernet, Data Saver off → [DataUsageMode.Unrestricted]
 */
class DefaultDataUsagePolicy(
    private val networkSaverModeProvider: NetworkSaverModeProvider,
) : DataUsagePolicy {
    override val policy: Flow<DataUsageMode> =
        networkSaverModeProvider.dataSaverModeState.map { it.toDataUsageMode() }

    override suspend fun currentMode(): DataUsageMode = networkSaverModeProvider.getCurrentDataSaverState().toDataUsageMode()
}

internal fun NetworkSaverState.toDataUsageMode(): DataUsageMode =
    when {
        connectionType == NetworkConnectionType.NONE -> DataUsageMode.Restricted
        isDataSaverEnabled -> DataUsageMode.Restricted
        connectionType == NetworkConnectionType.CELLULAR -> DataUsageMode.Conservative
        else -> DataUsageMode.Unrestricted
    }
