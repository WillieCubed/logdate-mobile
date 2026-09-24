package app.logdate.client.networking.saver

/**
 * Represents Android's background-data status and the active network's transport and cost.
 */
data class NetworkSaverState(
    val isDataSaverEnabled: Boolean,
    val connectionType: NetworkConnectionType,
    val isMetered: Boolean = connectionType == NetworkConnectionType.CELLULAR,
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
