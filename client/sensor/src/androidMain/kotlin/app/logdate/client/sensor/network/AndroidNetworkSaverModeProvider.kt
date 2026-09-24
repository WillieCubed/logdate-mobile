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
            override fun onAvailable(network: Network) {
                networkSaverStateFlow.value = getCurrentNetworkSaverStateInternal()
            }

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

    override suspend fun getCurrentDataSaverState(): NetworkSaverState =
        getCurrentNetworkSaverStateInternal().also { networkSaverStateFlow.value = it }

    override suspend fun isDataSaverModeActive(): Boolean = getCurrentDataSaverState().isDataSaverEnabled

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
        val isBackgroundDataRestricted =
            try {
                connectivityManager.restrictBackgroundStatus ==
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            } catch (e: Exception) {
                false
            }

        val activeCapabilities = getActiveNetworkCapabilities()

        return NetworkSaverState(
            isDataSaverEnabled = isBackgroundDataRestricted,
            connectionType = activeCapabilities.toConnectionType(),
            isMetered = runCatching { connectivityManager.isActiveNetworkMetered }.getOrDefault(false),
        )
    }

    private fun getActiveNetworkCapabilities(): NetworkCapabilities? =
        try {
            val activeNetwork = connectivityManager.activeNetwork
            activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        } catch (e: Exception) {
            null
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
