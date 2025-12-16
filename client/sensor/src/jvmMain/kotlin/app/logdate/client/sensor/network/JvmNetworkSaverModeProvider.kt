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

    /**
     * Because there are no network saver modes on JVM platforms, this method always returns false.
     */
    override suspend fun isDataSaverModeActive(): Boolean {
        return false
    }

    /**
     * There are no resources to clean up for JVM, so this is a no-op.
     */
    override fun cleanup() {
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
            return NetworkConnectionType.NONE
        }
    }
}