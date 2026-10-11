package app.logdate.client.networking.saver

/**
 * Represents Android's background-data status and the active network's transport and cost.
 *
 * @property isValidated Whether the system confirmed the network reaches the internet; a captive portal
 *   or a dead access point is connected but not validated.
 */
data class NetworkSaverState(
    val isDataSaverEnabled: Boolean,
    val connectionType: NetworkConnectionType,
    val isMetered: Boolean = connectionType == NetworkConnectionType.CELLULAR || connectionType == NetworkConnectionType.BLUETOOTH_PROXY,
    val isValidated: Boolean = connectionType != NetworkConnectionType.NONE,
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

    /** A watch's connection through its phone over Bluetooth: slow, and the phone's own data. */
    BLUETOOTH_PROXY,
}
