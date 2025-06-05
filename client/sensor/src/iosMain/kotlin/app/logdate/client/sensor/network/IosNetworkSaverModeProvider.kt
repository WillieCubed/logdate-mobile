package app.logdate.client.sensor.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.URLSessionConfiguration
import platform.Network.NWPathMonitor
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_interface_type_wired
import platform.Network.nw_path_status_t
import platform.darwin.dispatch_get_main_queue

class IosNetworkSaverModeProvider : NetworkSaverModeProvider {
    
    private val _networkSaverState = MutableStateFlow(getCurrentNetworkSaverStateInternal())
    
    // URLSessionConfiguration gives us access to constrained network access settings
    private val sessionConfig = URLSessionConfiguration.defaultSessionConfiguration
    
    // NWPathMonitor gives us real-time network path information
    private val pathMonitor = NWPathMonitor()
    private var connectionType = NetworkConnectionType.WIFI // Default assumption
    
    init {
        // Start monitoring network path changes
        pathMonitor.pathUpdateHandler = { path ->
            // Update connection type based on path
            connectionType = when {
                path.usesInterfaceType(nw_interface_type_wifi) -> NetworkConnectionType.WIFI
                path.usesInterfaceType(nw_interface_type_cellular) -> NetworkConnectionType.CELLULAR
                path.usesInterfaceType(nw_interface_type_wired) -> NetworkConnectionType.ETHERNET
                else -> NetworkConnectionType.OTHER
            }
            
            // If not connected, set to NONE
            if (path.status == nw_path_status_t) {
                connectionType = NetworkConnectionType.NONE
            }
            
            // Update the network state
            _networkSaverState.value = getCurrentNetworkSaverStateInternal()
        }
        
        // Start the monitor on the main dispatch queue
        pathMonitor.start(queue = dispatch_get_main_queue())
    }
    
    override val dataSaverModeState: Flow<NetworkSaverState> = _networkSaverState.asStateFlow()
    
    override suspend fun getCurrentDataSaverState(): NetworkSaverState {
        return getCurrentNetworkSaverStateInternal()
    }
    
    override suspend fun isDataSaverModeActive(): Boolean {
        // iOS has Low Data Mode which can be detected through URLSessionConfiguration
        // !allowsConstrainedNetworkAccess means Low Data Mode is active
        return !sessionConfig.allowsConstrainedNetworkAccess
    }
    
    override fun cleanup() {
        // Cancel the path monitor
        pathMonitor.cancel()
    }
    
    private fun getCurrentNetworkSaverStateInternal(): NetworkSaverState {
        val isDataSaverEnabled = !sessionConfig.allowsConstrainedNetworkAccess
        
        return NetworkSaverState(
            isDataSaverEnabled = isDataSaverEnabled,
            connectionType = connectionType
        )
    }
}