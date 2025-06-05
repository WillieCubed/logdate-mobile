package app.logdate.client.sensor.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface

/**
 * Implementation of NetworkSaverModeProvider for JVM platforms.
 * 
 * This implementation provides reasonable defaults for JVM platforms
 * and attempts to detect network interface availability.
 */
class JvmNetworkSaverModeProvider : NetworkSaverModeProvider {
    
    private val _networkSaverState = MutableStateFlow(getCurrentNetworkSaverStateInternal())
    
    override val dataSaverModeState: Flow<NetworkSaverState> = _networkSaverState.asStateFlow()
    
    override suspend fun getCurrentDataSaverState(): NetworkSaverState {
        return _networkSaverState.value
    }
    
    override suspend fun isDataSaverModeActive(): Boolean {
        // JVM platforms don't have a concept of data saver mode
        return false
    }
    
    override fun cleanup() {
        // No-op for JVM
    }
    
    private fun getCurrentNetworkSaverStateInternal(): NetworkSaverState {
        // JVM doesn't have a data saver mode
        val isDataSaverEnabled = false
        
        // Get current connection type
        val connectionType = getCurrentConnectionType()
        
        return NetworkSaverState(
            isDataSaverEnabled = isDataSaverEnabled,
            connectionType = connectionType
        )
    }
    
    private fun getCurrentConnectionType(): NetworkConnectionType {
        try {
            // Check if any network interface is up
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            
            if (networkInterfaces != null) {
                while (networkInterfaces.hasMoreElements()) {
                    val networkInterface = networkInterfaces.nextElement()
                    
                    // Skip loopback and inactive interfaces
                    if (networkInterface.isLoopback || !networkInterface.isUp) {
                        continue
                    }
                    
                    // If we have any active non-loopback interface, assume we're connected
                    // For desktop, we typically assume ethernet/wifi rather than cellular
                    return NetworkConnectionType.ETHERNET
                }
            }
            
            return NetworkConnectionType.NONE
        } catch (e: Exception) {
            // If we can't determine network state, assume NONE
            return NetworkConnectionType.NONE
        }
    }
}