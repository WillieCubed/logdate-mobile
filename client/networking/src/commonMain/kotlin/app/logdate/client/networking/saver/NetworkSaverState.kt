package app.logdate.client.networking.saver

/**
 * Represents the current state of the device's data saver mode.
 */
data class NetworkSaverState(
    val isDataSaverEnabled: Boolean,
    val connectionType: NetworkConnectionType,
)

/**
 * Represents the type of network connection the device is currently using.
 */
enum class NetworkConnectionType {
    NONE,
    CELLULAR,
    WIFI,
    ETHERNET,
    OTHER,
}
