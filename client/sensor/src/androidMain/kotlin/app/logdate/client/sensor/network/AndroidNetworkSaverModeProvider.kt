package app.logdate.client.sensor.network

import android.content.Context
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of [NetworkSaverModeProvider] that monitors data saver mode
 * and network connection type using the Android ConnectivityManager.
 */
class AndroidNetworkSaverModeProvider(
    private val context: Context
) : NetworkSaverModeProvider {
    
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val _networkSaverState = MutableStateFlow(getCurrentNetworkSaverStateInternal())
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _networkSaverState.value = getCurrentNetworkSaverStateInternal()
        }
        
        override fun onLost(network: Network) {
            _networkSaverState.value = getCurrentNetworkSaverStateInternal()
        }
    }
    
    private val dataSaverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _networkSaverState.value = getCurrentNetworkSaverStateInternal()
        }
    }
    
    init {
        // Register for network changes
        val request = NetworkRequest.Builder().build()
        try {
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
    
    override val dataSaverModeState: Flow<NetworkSaverState> = _networkSaverState.asStateFlow()
    
    override suspend fun getCurrentDataSaverState(): NetworkSaverState {
        return _networkSaverState.value
    }
    
    override suspend fun isDataSaverModeActive(): Boolean {
        return _networkSaverState.value.isDataSaverEnabled
    }
    
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
        val isDataSaverEnabled = try {
            connectivityManager.restrictBackgroundStatus == 
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } catch (e: Exception) {
            false
        }
            
        val connectionType = getCurrentConnectionType()
        
        return NetworkSaverState(
            isDataSaverEnabled = isDataSaverEnabled,
            connectionType = connectionType
        )
    }
    
    private fun getCurrentConnectionType(): NetworkConnectionType {
        try {
            val activeNetwork = connectivityManager.activeNetwork ?: return NetworkConnectionType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) 
                ?: return NetworkConnectionType.NONE
            
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkConnectionType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkConnectionType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkConnectionType.ETHERNET
                else -> NetworkConnectionType.OTHER
            }
        } catch (e: Exception) {
            return NetworkConnectionType.OTHER
        }
    }
}