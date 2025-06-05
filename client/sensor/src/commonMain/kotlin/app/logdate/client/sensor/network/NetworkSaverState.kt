package app.logdate.client.sensor.network

/**
 * Represents the current state of the device's data saver mode.
 * 
 * @property isDataSaverEnabled Whether the system-wide data saver mode is active
 * @property connectionType The current network connection type
 */
data class NetworkSaverState(
    val isDataSaverEnabled: Boolean,
    val connectionType: NetworkConnectionType
)

/**
 * Represents the type of network connection the device is currently using.
 */
enum class NetworkConnectionType {
    /**
     * Device is not connected to any network
     */
    NONE,
    
    /**
     * Device is connected via mobile/cellular data
     */
    CELLULAR,
    
    /**
     * Device is connected via WiFi
     */
    WIFI,
    
    /**
     * Device is connected via ethernet (typically desktop only)
     */
    ETHERNET,
    
    /**
     * Device is connected via an unknown or unspecified connection type
     */
    OTHER
}