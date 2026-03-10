package app.logdate.client.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * A monitor for network availability.
 *
 * This should be used to determine whether the device is connected to the internet.
 */
class AndroidNetworkAvailabilityMonitor(
    context: Context,
) : NetworkAvailabilityMonitor {
    private val connectivityManager = context.getSystemService() as ConnectivityManager?
    private val networkState = MutableStateFlow(currentNetworkState())
    private val connectivityCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                refreshNetworkState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                refreshNetworkState()
            }

            override fun onUnavailable() {
                refreshNetworkState()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                refreshNetworkState()
            }
        }

    init {
        val manager = connectivityManager
        if (manager == null) {
            Napier.w(tag = "NetworkAvailabilityMonitor", message = "ConnectivityManager is null")
        } else {
            runCatching {
                manager.registerDefaultNetworkCallback(connectivityCallback)
            }.onFailure { error ->
                Napier.e("Failed to register default network callback", error, tag = "NetworkAvailabilityMonitor")
            }
        }
    }

    override fun isNetworkAvailable(): Boolean {
        val manager = connectivityManager ?: return false
        val activeNetwork = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Subscribes an observer to the network state.
     *
     * The returned flow is hot and always has the latest known connectivity state.
     */
    override fun observeNetwork(): SharedFlow<NetworkState> = networkState.asStateFlow()

    private fun refreshNetworkState() {
        networkState.value = currentNetworkState()
    }

    private fun currentNetworkState(): NetworkState =
        if (isNetworkAvailable()) {
            NetworkState.Connected(Clock.System.now())
        } else {
            NetworkState.NotConnected(Clock.System.now())
        }
}
