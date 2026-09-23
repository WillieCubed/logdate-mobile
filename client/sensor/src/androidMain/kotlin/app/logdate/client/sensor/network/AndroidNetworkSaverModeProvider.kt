package app.logdate.client.sensor.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.networking.saver.NetworkSaverState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of [NetworkSaverModeProvider] that monitors data saver mode
 * and network connection type using the Android ConnectivityManager.
 */
class AndroidNetworkSaverModeProvider(
    private val context: Context,
) : NetworkSaverModeProvider {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val networkSaverStateFlow = MutableStateFlow(getCurrentNetworkSaverStateInternal())

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                networkSaverStateFlow.value = getCurrentNetworkSaverStateInternal()
            }

            override fun onLost(network: Network) {
                networkSaverStateFlow.value = getCurrentNetworkSaverStateInternal()
            }
        }

    private val dataSaverReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                networkSaverStateFlow.value = getCurrentNetworkSaverStateInternal()
            }
        }

    init {
        // Register for network changes
        try {
            val request = NetworkRequest.Builder().build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            // In case of security exception or other issues
        }

        // Register for data saver mode changes
        try {
            val filter = IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
            context.registerReceiver(dataSaverReceiver, filter)
        } catch (e: Exception) {
            // In case of security exception or other issues
        }
    }

    override val dataSaverModeState: Flow<NetworkSaverState> = networkSaverStateFlow.asStateFlow()

    override suspend fun getCurrentDataSaverState(): NetworkSaverState = networkSaverStateFlow.value

    override suspend fun isDataSaverModeActive(): Boolean = networkSaverStateFlow.value.isDataSaverEnabled

    override fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore if not registered
        }

        try {
            context.unregisterReceiver(dataSaverReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }

    private fun getCurrentNetworkSaverStateInternal(): NetworkSaverState {
        val isGlobalDataSaverEnabled =
            try {
                connectivityManager.restrictBackgroundStatus ==
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            } catch (e: Exception) {
                false
            }

        val activeCapabilities = getActiveNetworkCapabilities()

        return NetworkSaverState(
            // Global Data Saver only reports the device-wide toggle. A user can leave that off
            // and still turn off Background data for LogDate specifically (Settings > Apps >
            // LogDate > Data usage), which restrictBackgroundStatus alone never sees - sync would
            // keep getting silently dropped by the platform with no signal anywhere in the app.
            isDataSaverEnabled = isGlobalDataSaverEnabled || isBackgroundDataRestrictedForThisApp(activeCapabilities),
            connectionType = activeCapabilities.toConnectionType(),
        )
    }

    private fun getActiveNetworkCapabilities(): NetworkCapabilities? =
        try {
            val activeNetwork = connectivityManager.activeNetwork
            activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        } catch (e: Exception) {
            null
        }

    /**
     * [NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED] reflects, for the calling app's UID,
     * the combined effect of system-wide Data Saver, this app's own per-app background-data
     * restriction, and Doze/App Standby - exactly the platform-side "can this app use the
     * network right now" answer that [ConnectivityManager.getRestrictBackgroundStatus] can't
     * give on its own. Its absence on the active network is treated the same as Data Saver being
     * on: sync should pause and the user should see why.
     */
    private fun isBackgroundDataRestrictedForThisApp(capabilities: NetworkCapabilities?): Boolean =
        try {
            capabilities != null && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        } catch (e: Exception) {
            false
        }

    private fun NetworkCapabilities?.toConnectionType(): NetworkConnectionType {
        if (this == null) return NetworkConnectionType.NONE
        return try {
            when {
                hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkConnectionType.WIFI
                hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkConnectionType.CELLULAR
                hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkConnectionType.ETHERNET
                else -> NetworkConnectionType.OTHER
            }
        } catch (e: Exception) {
            NetworkConnectionType.OTHER
        }
    }
}
